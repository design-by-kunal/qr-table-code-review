package com.gulfnet.usermanagement.validator;

import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.model.request.BulkUserUploadRequest;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.ShiftRepository;
import com.gulfnet.shared_library.repository.ShiftTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.usermanagement.util.MessageUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j  
public class BulkUploadValidator {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupRepository restaurantGroupRepository;
    private final MessageUtil messageUtil;

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String PHONE_REGEX = "^\\+?\\d{7,14}$";
    private static final String NAME_REGEX = "^[a-zA-Z\\s]{1,50}$";
    private static final String USER_CODE_REGEX = "^[A-Za-z0-9]{1,10}$";
    private static final String RESTAURANT_CODE_REGEX = "^[A-Za-z0-9]{1,10}$";
    private static final String GROUP_CODE_REGEX = "^[A-Za-z0-9]{1,10}$";

    /**
     * Validates the uploaded bulk user CSV file for basic constraints such as
     * non-emptiness and correct content type (text/csv). Uses a default English
     * locale because language is not yet known at this stage.
     *
     * @param file the multipart file to validate
     * @throws BadRequestException if the file is empty or not a CSV file
     */
    public void validateFile(MultipartFile file) {
        // Use default locale for file validation since this is called before language is determined
        Locale defaultLocale = new Locale("en");
        
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(messageUtil.getMessage("bulk.upload.error.file.empty", defaultLocale));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("text/csv")) {
            throw new BadRequestException(messageUtil.getMessage("bulk.upload.error.file.type", defaultLocale));
        }
    }

    public void validateUserRequest(BulkUserUploadRequest user, String language) {
        Locale userLocale = getLocaleFromLanguage(language);
        java.util.List<String> validationErrors = new java.util.ArrayList<>();
        
        validateUserRequestCommon(user, userLocale, validationErrors);
        
        // If any validation errors found, throw exception with all errors
        if (!validationErrors.isEmpty()) {
            throw new BadRequestException(String.join("; ", validationErrors));
        }
    }

    /**
     * Validates user request with image validation support.
     * Collects all validation errors first, then throws a single exception with all errors.
     */
    public void validateUserRequestWithImages(BulkUserUploadRequest user, String language, 
                                             Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        Locale userLocale = getLocaleFromLanguage(language);
        java.util.List<String> validationErrors = new java.util.ArrayList<>();
        
        validateUserRequestCommon(user, userLocale, validationErrors);
        
        // Additional image validation
        try {
            validateImage(user, user.getImageName().trim(), imageMap, imageMapping, user.getUserCode(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        // If any validation errors found, throw exception with all errors
        if (!validationErrors.isEmpty()) {
            throw new BadRequestException(String.join("; ", validationErrors));
        }
    }

    /**
     * Common validation logic shared by validateUserRequest and validateUserRequestWithImages.
     * Performs all base user validations and collects errors.
     */
    private void validateUserRequestCommon(BulkUserUploadRequest user, Locale userLocale, 
                                          java.util.List<String> validationErrors) {
        // Run all validations and collect errors
        try {
            validateUserCode(user.getUserCode(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateName(user.getFirstName(), "First name", userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateName(user.getLastName(), "Last name", userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateEmail(user.getEmail(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validatePhone(user.getMobileNumber(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateRole(user.getRole(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateRestaurantCode(user.getRestaurantCode(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateGroupCode(user.getRestaurantGroupCode(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateLanguage(user.getLanguageCode(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateStatus(user.getStatus(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateEmploymentType(user.getEmploymentType(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
        
        try {
            validateShift(user.getShift(), userLocale);
        } catch (Exception e) {
            validationErrors.add(e.getMessage());
        }
    }

    /**
     * Helper method to get Locale from language string.
     */
    private Locale getLocaleFromLanguage(String language) {
        if ("ja".equalsIgnoreCase(language)) {
            return new Locale("ja");
        } else if ("th".equalsIgnoreCase(language)) {
            return new Locale("th");
        } else {
            return new Locale("en");
        }
    }

    /**
     * Validates user code format and uniqueness using a case-insensitive check
     * to prevent duplicates such as "abc", "ABC", or "Abc".
     *
     * @param userCode   the user code to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if user code is missing, invalid, or already exists
     */
    private void validateUserCode(String userCode, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(userCode)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.usercode.required", userLocale));
        } else {
            if (!Pattern.matches(USER_CODE_REGEX, userCode)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.usercode", userLocale));
            }
            // Use case-insensitive check to prevent duplicates like "abc", "ABC", "Abc"
            String normalizedUserCode = userCode.trim().toLowerCase();
            if (userRepository.existsByUserCodeIgnoreCase(normalizedUserCode)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.usercode.exists", userLocale, userCode));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates a user's first or last name against a simple name pattern and
     * ensures the field is not empty.
     *
     * @param name       the name value to validate
     * @param fieldName  human-readable field label (e.g., "First name")
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if the name is missing or does not match the expected pattern
     */
    private void validateName(String name, String fieldName, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(name)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.name.required", userLocale, fieldName));
        } else {
            if (!Pattern.matches(NAME_REGEX, name)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.name", userLocale));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates email presence, format, and uniqueness.
     *
     * @param email      the email address to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if email is missing, invalid, or already exists
     */
    private void validateEmail(String email, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(email)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.email.required", userLocale));
        } else {
            if (!Pattern.matches(EMAIL_REGEX, email)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.email", userLocale));
            }
            if (userRepository.existsByEmailIgnoreCase(email)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.email.exists", userLocale, email));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates phone number presence and basic numeric pattern (with optional
     * leading plus sign), after normalizing spaces and hyphens.
     *
     * @param phone      the phone number to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if phone is missing or invalid
     */
    private void validatePhone(String phone, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(phone)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.phone.required", userLocale));
        } else {
            // Remove any spaces or hyphens from the number
            String cleanNumber = phone.replaceAll("[\\s-]", "");

            if (!Pattern.matches(PHONE_REGEX, cleanNumber)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.phone", userLocale));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates that role is provided and exists in the role repository.
     *
     * @param role       the role name to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if role is missing or does not exist
     */
    private void validateRole(String role, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(role)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.role.required", userLocale));
        } else {
            if (!roleRepository.existsByName(role)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.role", userLocale));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates restaurant code format and verifies that a corresponding active
     * restaurant exists in the database.
     *
     * @param code       the restaurant code to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if code is missing, invalid, not found, or inactive
     */
    private void validateRestaurantCode(String code, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(code)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.restaurant.code.required", userLocale));
        } else {
            log.info("Validating restaurant code: '{}'", code);
            
            if (!Pattern.matches(RESTAURANT_CODE_REGEX, code)) {
                log.error("Restaurant code format validation failed for: '{}'", code);
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.restaurant.code", userLocale));
            } else {
                log.info("Restaurant code format validation passed for: '{}'", code);

                Optional<Restaurant> restaurantOpt = restaurantRepository.findByRestaurantCodeAndIsDeletedFalse(code);

                if (restaurantOpt.isEmpty()) {
                    log.error("Restaurant code not found: '{}'", code);
                    errors.add(messageUtil.getMessage("bulk.upload.error.restaurant.code.not.found", userLocale, code));
                } else {
                    Restaurant restaurant = restaurantOpt.get();

                    if (restaurant.getStatus() != EntityStatus.ACTIVE) {
                        log.error("Restaurant '{}' is not active. Current status: {}", code, restaurant.getStatus());
                        errors.add(messageUtil.getMessage("bulk.upload.error.restaurant.inactive", userLocale, code));
                    } else {
                        log.info("Restaurant code validation successful for: '{}'", code);
                    }
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates restaurant group code format and verifies that a corresponding
     * active group exists in the database.
     *
     * @param code       the restaurant group code to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if code is missing, invalid, not found, or inactive
     */
    private void validateGroupCode(String code, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(code)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.group.code.required", userLocale));
        } else {
            log.info("Validating group code: '{}'", code);
            
            // First validate format
            if (!Pattern.matches(GROUP_CODE_REGEX, code)) {
                log.error("Group code format validation failed for: '{}'", code);
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.group.code", userLocale));
            } else {
                log.info("Group code format validation passed for: '{}'", code);
                
                // Then validate against database
                Optional<RestaurantGroup> groupOpt = restaurantGroupRepository.findByRestaurantGroupCodeAndIsDeletedFalse(code);

                if (groupOpt.isEmpty()) {
                    log.error("Group code not found in database: '{}'", code);
                    errors.add(messageUtil.getMessage("bulk.upload.error.group.code.not.found", userLocale, code));
                } else {
                    RestaurantGroup group = groupOpt.get();

                    // ✅ Check status
                    if (group.getStatus() != EntityStatus.ACTIVE) {
                        log.error("Group '{}' is inactive. Current status: {}", code, group.getStatus());
                        errors.add(messageUtil.getMessage("bulk.upload.error.group.inactive", userLocale, code));
                    } else {
                        log.info("Group code validation successful for: '{}'", code);
                    }
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }
    

    /**
     * Validates language code presence and ensures it is one of the supported
     * languages (en, ja, th).
     *
     * @param languageCode the language code to validate
     * @param userLocale   the locale used for localized error messages
     * @throws BadRequestException if language code is missing or unsupported
     */
    private void validateLanguage(String languageCode, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(languageCode)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.language.required", userLocale));
        } else {
            if (!Arrays.asList("en", "ja", "th").contains(languageCode)) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.language", userLocale));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates user status presence and ensures it is one of the allowed
     * values (ACTIVE or INACTIVE).
     *
     * @param status     the status to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if status is missing or invalid
     */
    private void validateStatus(String status, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        log.info("Validating status: '{}'", status);
        
        if (!StringUtils.hasText(status)) {
            log.error("Status validation failed: status is empty or null");
            errors.add(messageUtil.getMessage("bulk.upload.error.status.required", userLocale));
        } else {
            if (!status.matches("^(ACTIVE|INACTIVE)$")) {
                log.error("Status validation failed: invalid status value '{}'", status);
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.status", userLocale));
            } else {
                log.info("Status validation successful for: '{}'", status);
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates employment type presence and ensures it is one of the allowed
     * values (FULL_TIME or PART_TIME).
     *
     * @param employmentType the employment type to validate
     * @param userLocale     the locale used for localized error messages
     * @throws BadRequestException if employment type is missing or invalid
     */
    private void validateEmploymentType(String employmentType, Locale userLocale) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(employmentType)) {
            errors.add("Employment type is required");
        } else {
            if (!employmentType.matches("^(FULL_TIME|PART_TIME)$")) {
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.employment.type", userLocale));
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Validates shift presence and verifies that a corresponding shift exists
     * in the database via the shift translation repository.
     *
     * @param shift      the shift name to validate
     * @param userLocale the locale used for localized error messages
     * @throws BadRequestException if shift is missing or not found
     */
    private void validateShift(String shift, Locale userLocale) {
        List<String> errors = new java.util.ArrayList<>();
        
        if (!StringUtils.hasText(shift)) {
            errors.add(messageUtil.getMessage("bulk.upload.error.shift.required", userLocale));
        } else {
            log.info("Validating shift: '{}'", shift);
            
            List<Shift> shifts = shiftTranslationRepository.findShiftsByName(shift);
            boolean exists = !shifts.isEmpty();
            log.info("Database check for shift '{}': exists = {}", shift, exists);
            
            if (!exists) {
                log.error("Shift not found in database: '{}'", shift);
                errors.add(messageUtil.getMessage("bulk.upload.error.invalid.shift", userLocale, shift));
            } else {
                log.info("Shift validation successful for: '{}'", shift);
            }
        }
        
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    /**
     * Ensures that a manager can only upload users for their own restaurant by
     * comparing the manager's assigned restaurant with the restaurant derived
     * from the user's restaurant code.
     *
     * @param user           the bulk user upload request being validated
     * @param managerUserId  the ID of the manager performing the upload
     * @param managerRole    the role of the manager (validation applied only for MANAGER)
     * @param userLocale     the locale used for localized error messages
     * @throws BadRequestException if the manager has no restaurant or tries to upload for another restaurant
     */
    public void validateManagerRestaurantAccess(BulkUserUploadRequest user, String managerUserId, String managerRole, Locale userLocale) {
        // Only apply this validation for MANAGER role
        if (!"MANAGER".equalsIgnoreCase(managerRole)) {
            return;
        }
        
        log.info("Validating manager restaurant access for manager: {} with role: {}", managerUserId, managerRole);
        
        // Get manager's restaurant ID
        UUID managerId = UUID.fromString(managerUserId);
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new BadRequestException(messageUtil.getMessage("bulk.upload.error.manager.not.found", userLocale)));
        
        UUID managerRestaurantId = manager.getRestaurantId();
        if (managerRestaurantId == null) {
            log.error("Manager {} does not have a restaurant assigned", managerUserId);
            throw new BadRequestException(messageUtil.getMessage("bulk.upload.error.manager.no.restaurant", userLocale));
        }
        
        // Get user's restaurant ID from restaurant code
        if (StringUtils.hasText(user.getRestaurantCode())) {
            Optional<Restaurant> userRestaurantOpt = restaurantRepository.findByRestaurantCodeAndIsDeletedFalse(user.getRestaurantCode());
            if (userRestaurantOpt.isPresent()) {
                UUID userRestaurantId = userRestaurantOpt.get().getId();
                
                log.info("Manager restaurant ID: {}, User restaurant ID: {}", managerRestaurantId, userRestaurantId);
                
                if (!managerRestaurantId.equals(userRestaurantId)) {
                    log.error("Manager {} cannot upload users for restaurant {} (manager's restaurant: {})", 
                            managerUserId, user.getRestaurantCode(), managerRestaurantId);
                    throw new BadRequestException(messageUtil.getMessage("bulk.upload.error.manager.restaurant.mismatch", 
                            userLocale, user.getRestaurantCode()));
                }
                
                log.info("Manager restaurant access validation successful for user: {}", user.getUserCode());
            }
        }
    }

    /**
     * Validates image file if provided.
     * Checks if the image exists in the ZIP archive.
     */
    private void validateImage(BulkUserUploadRequest user, String imageName, Map<String, byte[]> imageMap, Map<String, String> imageMapping, 
                              String userCode, Locale userLocale) {

        java.util.List<String> errors = new java.util.ArrayList<>();

        if (imageMap != null && imageMapping != null && user.getImageName() != null && !user.getImageName().trim().isEmpty()) {   
            // Get the renamed filename from the mapping
            String renamedImageFileName = imageMapping.get(imageName);
            
            // Validate that the image filename exists in the ZIP
            if (renamedImageFileName == null) {
                errors.add(messageUtil.getMessage("bulk.upload.error.image.file.not.found", userLocale, imageName));
            } else {
                // Get image data from the map using renamed filename
                byte[] imageData = imageMap.get(renamedImageFileName);
                
                if (imageData == null) {
                    errors.add(messageUtil.getMessage("bulk.upload.error.image.file.not.found", userLocale, imageName));
                }
                // Validate image size (<= 1MB)
                if (imageData != null && imageData.length > 1_048_576) {
                    errors.add(messageUtil.getMessage("bulk.upload.file.size.exceeded", userLocale, "1MB"));
                }
            }
            
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }
} 
