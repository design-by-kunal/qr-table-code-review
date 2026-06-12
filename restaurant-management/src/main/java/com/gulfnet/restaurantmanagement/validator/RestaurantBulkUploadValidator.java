package com.gulfnet.restaurantmanagement.validator;

import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.config.LanguageConfiguration;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.model.request.BulkRestaurantUploadRequest;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.ValidationException;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestaurantBulkUploadValidator {

    private final MessageUtil messageUtil;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupRepository restaurantGroupRepository;
    private final RestaurantTranslationRepository restaurantTranslationRepository;
    private final LanguageConfiguration languageConfiguration;
    
    /**
     * Get supported languages from configuration
     */
    private List<LanguageConfiguration.LanguageConfig> getSupportedLanguages() {
        List<LanguageConfiguration.LanguageConfig> languages = languageConfiguration.getSupportedLanguages() != null ? 
            languageConfiguration.getSupportedLanguages() : new ArrayList<>();
        log.info("LanguageConfiguration.getSupportedLanguages(): {}", languageConfiguration.getSupportedLanguages());
        log.info("Returning languages: {}", languages);
        return languages;
    }

    // Generic pattern for all languages - no hardcoded language-specific patterns
    private static final Pattern GENERIC_NAME_PATTERN = Pattern.compile("^.{1,50}$");
    
    // Use possessive quantifiers to prevent catastrophic backtracking
    // Removed lookahead and validate length separately to avoid stack overflow
    /** Possessive quantifiers avoid polynomial-time backtracking on long inputs (Sonar java:S5998). */
    private static final Pattern NAME_CITY_STATE_PATTERN = Pattern.compile("^[A-Za-z]++(?:[ '&-][A-Za-z]++)*+$");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^[A-Za-z0-9]++(?:[ '&@#%.,;:/()\\-][A-Za-z0-9]++)*+$");
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_ADDRESS_LENGTH = 150;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{5,7}$");
    /** Same rules as {@code RestaurantRequest} phone (optional in bulk CSV). */
    private static final Pattern RESTAURANT_PHONE_PATTERN = Pattern.compile(
            "^(?=(?:[^\\d]*\\d){7,15}[^\\d]*$)[+\\d\\s().-]{7,32}$");

    public void validate(BulkRestaurantUploadRequest request) {
        validate(request, null, null);
    }

    /**
     * Validates a bulk restaurant upload request including language names, uniqueness checks,
     * location fields, address fields, codes, GST number, and logo validation.
     * Supports dynamic language validation based on configured languages.
     *
     * @param request      the bulk restaurant upload request to validate
     * @param imageMap     optional map of image file names to image data bytes
     * @param imageMapping optional map of original image names to renamed file names
     * @throws ValidationException if validation fails with detailed error messages
     */
    public void validate(BulkRestaurantUploadRequest request, Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        Locale userLocale = LocaleContextHolder.getLocale();
        List<String> errors = new ArrayList<>();
        List<String> gstErrors = new ArrayList<>();

        validateLanguageNames(request, errors, userLocale);
        validateNameUniqueness(request, errors, userLocale);
        validateLocationFields(request, errors, userLocale);
        validateAddressFields(request, errors, userLocale);
        validateRestaurantCode(request, errors, userLocale);
        validateRestaurantGroupCode(request, errors, userLocale);
        validateLocationPin(request, errors, userLocale);
        validateEnumField(request.getQrCodeType(), "bulk.restaurant.upload.error.qrcode.type.required",
                "bulk.restaurant.upload.error.qrcode.type.invalid", QrCodeType.class, errors, userLocale);
        validateEnumField(request.getStatus(), "status.required",
                "bulk.restaurant.upload.error.status.invalid", EntityStatus.class, errors, userLocale);
        validateGstNumber(request, errors, gstErrors, userLocale);
        validatePhoneNumber(request, errors, userLocale);
        validateLogo(request, imageMap, imageMapping, errors, userLocale);
        throwValidationExceptionIfNeeded(errors, gstErrors);
    }

    /**
     * Validates name uniqueness based on restaurant group code.
     */
    private void validateNameUniqueness(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        if (request.getRestaurantGroupCode() != null && !request.getRestaurantGroupCode().trim().isEmpty()) {
            RestaurantGroup group = restaurantGroupRepository
                    .findByRestaurantGroupCodeAndIsDeletedFalse(request.getRestaurantGroupCode().trim())
                    .orElse(null);
            if (group != null) {
                validateNameUniquenessInGroup(request, group, errors, userLocale);
            }
        } else {
            validateNameUniquenessGlobally(request, errors, userLocale);
        }
    }

    /**
     * Validates location fields: city, area, and state.
     */
    private void validateLocationFields(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        validateFieldWithPatternAndLength(request.getCity(), "bulk.restaurant.upload.error.city.required",
                "bulk.restaurant.upload.error.city.pattern", NAME_CITY_STATE_PATTERN, MAX_NAME_LENGTH, errors, userLocale);
        validateFieldWithPatternAndLength(request.getArea(), "bulk.restaurant.upload.error.area.required",
                "bulk.restaurant.upload.error.area.pattern", NAME_CITY_STATE_PATTERN, MAX_NAME_LENGTH, errors, userLocale);
        validateFieldWithPatternAndLength(request.getState(), "bulk.restaurant.upload.error.state.required",
                "bulk.restaurant.upload.error.state.pattern", NAME_CITY_STATE_PATTERN, MAX_NAME_LENGTH, errors, userLocale);
    }

    /**
     * Validates address fields: line 1 (required) and line 2 (optional).
     */
    private void validateAddressFields(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        validateFieldWithPatternAndLength(request.getAddressLine1(), "bulk.restaurant.upload.error.address.line1.required",
                "bulk.restaurant.upload.error.address.line1.pattern", ADDRESS_PATTERN, MAX_ADDRESS_LENGTH, errors, userLocale);
        if (!isBlank(request.getAddressLine2())) {
            String line2 = request.getAddressLine2();
            if (line2.length() > MAX_ADDRESS_LENGTH || !ADDRESS_PATTERN.matcher(line2).matches()) {
                errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.address.line2.pattern", userLocale, line2));
            }
        }
    }

    /**
     * Validates a field with pattern matching.
     */
    private void validateFieldWithPattern(String value, String requiredErrorKey, String patternErrorKey,
                                         Pattern pattern, List<String> errors, Locale userLocale) {
        if (isBlank(value)) {
            errors.add(messageUtil.getMessage(requiredErrorKey, userLocale));
        } else if (!pattern.matcher(value).matches()) {
            errors.add(messageUtil.getMessage(patternErrorKey, userLocale, value));
        }
    }
    
    /**
     * Validates a field with pattern matching and length check.
     * Length is checked first to avoid expensive regex matching on overly long strings.
     */
    private void validateFieldWithPatternAndLength(String value, String requiredErrorKey, String patternErrorKey,
                                                  Pattern pattern, int maxLength, List<String> errors, Locale userLocale) {
        if (isBlank(value)) {
            errors.add(messageUtil.getMessage(requiredErrorKey, userLocale));
        } else if (value.length() > maxLength) {
            errors.add(messageUtil.getMessage(patternErrorKey, userLocale, value));
        } else if (!pattern.matcher(value).matches()) {
            errors.add(messageUtil.getMessage(patternErrorKey, userLocale, value));
        }
    }

    /**
     * Validates restaurant code.
     */
    private void validateRestaurantCode(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        if (isBlank(request.getRestaurantCode())) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.code.required", userLocale));
        } else if (!CODE_PATTERN.matcher(request.getRestaurantCode()).matches()) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.code.pattern", userLocale, request.getRestaurantCode()));
        } else if (restaurantRepository.existsByRestaurantCodeAndIsDeletedFalse(request.getRestaurantCode().trim())) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.code.exists", userLocale, request.getRestaurantCode()));
        }
    }

    /**
     * Validates restaurant group code.
     */
    private void validateRestaurantGroupCode(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        if (isBlank(request.getRestaurantGroupCode())) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.group.code.required", userLocale));
            return;
        }

        if (!CODE_PATTERN.matcher(request.getRestaurantGroupCode()).matches()) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.group.code.pattern", userLocale, request.getRestaurantGroupCode()));
            return;
        }

        RestaurantGroup group = restaurantGroupRepository
                .findByRestaurantGroupCodeAndIsDeletedFalse(request.getRestaurantGroupCode().trim())
                .orElse(null);

        if (group == null) {
            String msg = messageUtil.getMessage("bulk.restaurant.upload.error.group.code.not.found", userLocale, request.getRestaurantGroupCode());
            log.warn("Group error message: {}", msg);
            errors.add(msg);
        } else if (group.getStatus() != EntityStatus.ACTIVE) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.group.code.inactive", userLocale, request.getRestaurantGroupCode()));
        } else {
            validateNameExistsInGroup(request, group, errors, userLocale);
        }
    }

    /**
     * Validates if restaurant names exist within the same group.
     */
    private void validateNameExistsInGroup(BulkRestaurantUploadRequest request, RestaurantGroup group,
                                          List<String> errors, Locale userLocale) {
        validateNameExistsInGroupForLanguage(request.getNameEn(), "en", group.getId(), errors, userLocale);
        validateNameExistsInGroupForLanguage(request.getNameJa(), "ja", group.getId(), errors, userLocale);
        validateNameExistsInGroupForLanguage(request.getNameTh(), "th", group.getId(), errors, userLocale);
    }

    /**
     * Validates if a specific language name exists in the same group.
     */
    private void validateNameExistsInGroupForLanguage(String name, String languageCode, UUID groupId,
                                                      List<String> errors, Locale userLocale) {
        if (name != null && !name.trim().isEmpty() &&
                restaurantTranslationRepository.existsByNameInSameGroup(name.trim(), groupId)) {
            errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.name." + languageCode + ".exists.in.group",
                    userLocale, name));
        }
    }

    /**
     * Validates location pin.
     */
    private void validateLocationPin(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        validateFieldWithPattern(request.getLocationPin(), "bulk.restaurant.upload.error.location.pin.required",
                "bulk.restaurant.upload.error.location.pin.pattern", PIN_PATTERN, errors, userLocale);
    }

    /**
     * Validates an enum field.
     */
    private <T extends Enum<T>> void validateEnumField(String value, String requiredErrorKey, String invalidErrorKey,
                                                        Class<T> enumClass, List<String> errors, Locale userLocale) {
        if (isBlank(value)) {
            errors.add(messageUtil.getMessage(requiredErrorKey, userLocale));
        } else {
            try {
                Enum.valueOf(enumClass, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(messageUtil.getMessage(invalidErrorKey, userLocale));
            }
        }
    }

    /**
     * Validates GST number.
     */
    private void validateGstNumber(BulkRestaurantUploadRequest request, List<String> errors, List<String> gstErrors,
                                   Locale userLocale) {
        if (isBlank(request.getGstNumber())) {
            String gstError = messageUtil.getMessage("bulk.restaurant.upload.error.gst.number.required", userLocale);
            gstErrors.add(gstError);
            errors.add(gstError);
        } else if (restaurantRepository.existsByGstNumberAndIsDeletedFalse(request.getGstNumber().trim(), null)) {
            String gstError = messageUtil.getMessage("bulk.restaurant.upload.error.gst.number.invalid", userLocale);
            gstErrors.add(gstError);
            errors.add(gstError);
        }
    }

    private void validatePhoneNumber(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        if (isBlank(request.getPhoneNumber())) {
            return;
        }
        String trimmed = request.getPhoneNumber().trim();
        if (!RESTAURANT_PHONE_PATTERN.matcher(trimmed).matches()) {
            errors.add(messageUtil.getMessage("restaurant.phoneNumber.invalid", userLocale));
        }
    }

    /**
     * Validates logo if provided.
     */
    private void validateLogo(BulkRestaurantUploadRequest request, Map<String, byte[]> imageMap,
                             Map<String, String> imageMapping, List<String> errors, Locale userLocale) {
        if (imageMap == null || imageMapping == null || isBlank(request.getLogoName())) {
            return;
        }

        try {
            String logoName = request.getLogoName().trim();
            String renamedLogoFileName = imageMapping.get(logoName);

            if (renamedLogoFileName == null) {
                errors.add(messageUtil.getMessage("bulk.upload.image.file.not.found", userLocale, logoName));
                return;
            }

            byte[] logoData = imageMap.get(renamedLogoFileName);
            if (logoData == null) {
                errors.add(messageUtil.getMessage("bulk.upload.image.file.not.found", userLocale, logoName));
            } else if (logoData.length > 1_048_576) {
                errors.add(messageUtil.getMessage("bulk.upload.file.size.exceeded", userLocale, "1MB"));
            }
        } catch (Exception e) {
            errors.add(messageUtil.getMessage("bulk.upload.image.validation.failed", userLocale, e.getMessage()));
        }
    }

    /**
     * Throws validation exception if there are any errors, prioritizing GST errors.
     */
    private void throwValidationExceptionIfNeeded(List<String> errors, List<String> gstErrors) {
        if (errors.isEmpty()) {
            return;
        }

        if (!gstErrors.isEmpty()) {
            throw new ValidationException(String.join("; ", gstErrors));
        }

        throw new ValidationException(String.join("; ", errors));
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Validate names for all configured languages dynamically
     */
    private void validateLanguageNames(BulkRestaurantUploadRequest request, List<String> errors, Locale userLocale) {
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            boolean isCompulsory = lang.isCompulsory();
            String name = request.getNameForLanguage(languageCode);
            
            if (isCompulsory && isBlank(name)) {
                String errorMsg = messageUtil.getMessage("bulk.restaurant.upload.error.name.required", 
                    userLocale, languageCode);
                errors.add(errorMsg);
            } else if (!isBlank(name) && !GENERIC_NAME_PATTERN.matcher(name).matches()) {
                String errorMsg = messageUtil.getMessage("bulk.restaurant.upload.error.name.pattern", 
                    userLocale, languageCode, name);
                errors.add(errorMsg);
            }
        }
    }
    
    /**
     * Validate name uniqueness within a group
     */
    private void validateNameUniquenessInGroup(BulkRestaurantUploadRequest request, RestaurantGroup group, 
                                            List<String> errors, Locale userLocale) {
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            String name = request.getNameForLanguage(languageCode);
            if (!isBlank(name) && 
                restaurantTranslationRepository.existsByNameInSameGroup(name.trim(), group.getId())) {
                errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.name.exists.in.group", 
                    userLocale, languageCode, name.trim()));
            }
        }
    }
    
    /**
     * Validate name uniqueness globally
     */
    private void validateNameUniquenessGlobally(BulkRestaurantUploadRequest request, 
                                              List<String> errors, Locale userLocale) {
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            String name = request.getNameForLanguage(languageCode);
            if (!isBlank(name) && 
                restaurantTranslationRepository.existsByNameIgnoreCase(name.trim())) {
                errors.add(messageUtil.getMessage("bulk.restaurant.upload.error.name.exists", 
                    userLocale, name.trim(), languageCode));
            }
        }
    }
}
