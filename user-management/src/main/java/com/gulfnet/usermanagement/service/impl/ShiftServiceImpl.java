package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.ShiftTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.ShiftRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.ShiftRepository;
import com.gulfnet.shared_library.repository.ShiftTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.UserShiftMappingRepository;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.usermanagement.config.LocalizationProperties;
import com.gulfnet.usermanagement.service.ShiftService;
import com.gulfnet.usermanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    private static final String MESSAGE_KEY_SHIFT_NOT_FOUND = "shift.not.found";

    private final ShiftRepository shiftRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final UserShiftMappingRepository userShiftMappingRepository;
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final LocalizationProperties localizationProperties;

    /**
     * Creates a new shift with translations for multiple languages.
     * Validates that shift names are unique per language and saves the shift
     * along with its translations.
     *
     * @param request the shift creation request containing start time, end time, status, and translations
     * @return {@link ResponseDto} containing the created shift data with translations
     * @throws ResponseStatusException if translations are missing or shift name already exists
     */
    @Override
    @Transactional
    public ResponseDto<ShiftDataResponse> createShift(ShiftRequest request) {
    
        Locale userLocale = LocaleContextHolder.getLocale();
    
        // Validate translations
        if (request.getTranslations() == null || request.getTranslations().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.create.error.translations.required", userLocale)
            );
        }
    
        // Check if shift name already exists for any language
        String defaultLanguage = getDefaultLanguage();
        
        for (ShiftTranslationDto translationDto : request.getTranslations()) {
            // Only validate non-empty names
            if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                boolean exists = shiftTranslationRepository.existsByNameIgnoreCaseAndLanguageCode(
                        translationDto.getName().trim(), translationDto.getLanguageCode());
                if (exists) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            messageUtil.getMessage("shift.create.error.exists", userLocale)
                    );
                }
            }
        }
    
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Shift shift = Shift.builder()
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(request.getStatus() != null ? request.getStatus() : EntityStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    
        shift = shiftRepository.save(shift);
    
        // Save translations
        List<ShiftTranslation> translations = new ArrayList<>();
        for (ShiftTranslationDto translationDto : request.getTranslations()) {
            // Only save non-empty translations
            if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                ShiftTranslation translation = ShiftTranslation.builder()
                        .shift(shift)
                        .languageCode(translationDto.getLanguageCode())
                        .name(translationDto.getName().trim())
                        .build();
                translations.add(translation);
            }
        }
        shiftTranslationRepository.saveAll(translations);
    
        // Build response with translations
        return buildShiftDataResponse(shift, translations, userLocale.getLanguage(), defaultLanguage, userLocale, "shift.create.success");
    }
    
    /**
     * Retrieves a paginated, filterable list of shifts with support for status filtering,
     * search by name, sorting, and soft-delete flag. Handles in-memory sorting for name-based
     * sorting to support translation-based ordering.
     *
     * <p>Supported sortBy values:</p>
     * <ul>
     *   <li><b>name</b> - Sorts by translated shift name (requires in-memory sorting)</li>
     *   <li><b>createdAt</b> - Sorts by creation date (database-level sorting, default)</li>
     *   <li><b>startTime</b> - Sorts by shift start time (database-level sorting)</li>
     *   <li>Other entity fields - Any direct Shift entity field can be sorted at database level</li>
     * </ul>
     *
     * @param page      page number (1-based, optional)
     * @param size      page size (optional, defaults to 10)
     * @param status    optional status filter (ACTIVE, INACTIVE)
     * @param search    optional search keyword for shift name
     * @param sortBy    field name to sort by (defaults to createdAt, "name" requires in-memory sorting)
     * @param direction sort direction (ASC or DESC, defaults to DESC)
     * @param isDeleted optional flag to include soft-deleted shifts
     * @param locale    optional locale for localized shift names
     * @return {@link ResponseDto} containing paginated list of shifts with translations
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ShiftListResponse> getAllShifts(Integer page, Integer size, String status, String search,
                                                       String sortBy, String direction,
                                                       Boolean isDeleted, String locale) {
        String localeString = getLocaleString(locale);
        Locale userLocale = Locale.forLanguageTag(localeString);
        String preferredLanguage = localeString;
        String defaultLanguage = getDefaultLanguage();
    
        PaginationParams pagination = validateAndNormalizePagination(page, size, userLocale);
        EntityStatus entityStatus = parseStatusFilter(status, userLocale);
        boolean deletedFlag = Boolean.TRUE.equals(isDeleted);
        Specification<Shift> spec = buildShiftSpecification(entityStatus, search, deletedFlag);
        
        // Normalize sortBy field - handle field name mapping if needed
        String dbSortField;
        if (sortBy != null && !sortBy.isBlank()) {
            // Keep sortBy as is, but check if it requires in-memory sorting
            dbSortField = sortBy;
        } else {
            dbSortField = "createdAt"; // Default sort by creation time
        }
        boolean requiresInMemorySorting = "name".equalsIgnoreCase(dbSortField);
        
        // Determine sort direction
        if (direction == null || direction.isBlank()) {
            direction = "DESC";
        }
        Sort.Direction sortDirection = 
            direction.equalsIgnoreCase("ASC") ? 
            Sort.Direction.ASC : 
            Sort.Direction.DESC;
        
        ShiftDataFetchResult fetchResult = fetchShiftData(spec, requiresInMemorySorting, dbSortField, sortDirection, 
                pagination.getPageNumber(), pagination.getPageSize());
        
        List<ShiftResponse> shiftResponses = buildShiftResponses(fetchResult.getShifts(), preferredLanguage, defaultLanguage);
        
        if (requiresInMemorySorting) {
            shiftResponses = applyInMemorySortingAndPagination(shiftResponses, sortDirection, 
                    pagination.getPageNumber(), pagination.getPageSize());
        }
    
        PaginationMetaData metaData = buildPaginationMetaData(pagination.getPageNumber(), pagination.getPageSize(), fetchResult.getTotalCount());
        ShiftListResponse data = ShiftListResponse.builder()
                .shifts(shiftResponses)
                .count((long) shiftResponses.size())
                .total(fetchResult.getTotalCount())
                .metaData(metaData)
                .build();
    
        Locale messageLocale = Locale.forLanguageTag(preferredLanguage);
        return ResponseDto.<ShiftListResponse>builder()
                .message(messageUtil.getMessage("shift.fetch.success", messageLocale))
                .data(data)
                .build();
    }
    
    /**
     * Validates and normalizes pagination parameters
     */
    private PaginationParams validateAndNormalizePagination(Integer page, Integer size, Locale userLocale) {
        int pageNumber = (page != null && page > 0 ? page : 1) - 1;
        if (page != null && page < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 1"
            );
        }
        int pageSize = size != null && size > 0 ? size : 10;
        return new PaginationParams(pageNumber, pageSize);
    }
    
    /**
     * Parses status filter string to EntityStatus enum
     */
    private EntityStatus parseStatusFilter(String status, Locale userLocale) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return EntityStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.fetch.invalid.status", userLocale)
            );
        }
    }
    
    /**
     * Builds JPA specification for filtering shifts
     * Handles filtering by status, search by name (with translations), and soft-delete flag.
     * When search is used, distinct is enabled to avoid duplicate results from translation joins.
     */
    private Specification<Shift> buildShiftSpecification(EntityStatus entityStatus, String search, boolean deletedFlag) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always filter by isDeleted flag
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), deletedFlag));
            
            // Filter by status if provided
            if (entityStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), entityStatus));
            }
            
            // Search by shift name in translations (case-insensitive)
            if (search != null && !search.trim().isEmpty()) {
                String searchTerm = search.trim();
                Join<Shift, ShiftTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                String searchPattern = "%" + searchTerm.toLowerCase() + "%";
                predicates.add(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(translationJoin.get("name")),
                        searchPattern
                    )
                );
                // Enable distinct to avoid duplicate shifts when multiple translations match
                query.distinct(true);
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Result class for shift data fetching
     */
    private static class ShiftDataFetchResult {
        private final List<Shift> shifts;
        private final long totalCount;
        
        ShiftDataFetchResult(List<Shift> shifts, long totalCount) {
            this.shifts = shifts;
            this.totalCount = totalCount;
        }
        
        List<Shift> getShifts() {
            return shifts;
        }
        
        long getTotalCount() {
            return totalCount;
        }
    }
    
    /**
     * Fetches shift data based on sorting requirements.
     * For "name" sorting, fetches all data for in-memory sorting (due to translations).
     * For other fields (like createdAt), uses database-level sorting with pagination.
     * 
     * @param spec the JPA specification for filtering
     * @param requiresInMemorySorting true if sorting by "name" (requires in-memory processing)
     * @param sortBy the field to sort by (already normalized)
     * @param direction the sort direction
     * @param pageNumber the page number (0-based)
     * @param pageSize the page size
     * @return ShiftDataFetchResult containing shifts and total count
     */
    private ShiftDataFetchResult fetchShiftData(Specification<Shift> spec, boolean requiresInMemorySorting, 
            String sortBy, Sort.Direction direction, int pageNumber, int pageSize) {
        if (requiresInMemorySorting) {
            // For name sorting, fetch all matching records (will be sorted in memory later)
            List<Shift> shifts = shiftRepository.findAll(spec);
            return new ShiftDataFetchResult(shifts, shifts.size());
        }
        
        // For database-level sorting (createdAt, startTime, etc.), use Pageable with Sort
        // sortBy is already normalized, so use it directly
        // Note: PostgreSQL handles nulls correctly by default (nulls last for DESC, nulls first for ASC)
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Shift> shiftPage = shiftRepository.findAll(spec, pageable);
        return new ShiftDataFetchResult(shiftPage.getContent(), shiftPage.getTotalElements());
    }
    
    /**
     * Builds shift response DTOs from shift entities
     */
    private List<ShiftResponse> buildShiftResponses(List<Shift> shifts, String preferredLanguage, String defaultLanguage) {
        List<UUID> shiftIds = shifts.stream().map(Shift::getId).collect(Collectors.toList());
        List<ShiftTranslation> allTranslations = shiftTranslationRepository.findAllByShiftIdIn(shiftIds);
        Map<UUID, List<ShiftTranslation>> translationsByShiftId = allTranslations.stream()
                .collect(Collectors.groupingBy(t -> t.getShift().getId()));
    
        return shifts.stream()
                .map(shift -> {
                    List<ShiftTranslation> translations = translationsByShiftId.getOrDefault(shift.getId(), new ArrayList<>());
                    List<ShiftTranslationDto> translationDtos = translations.stream()
                            .map(t -> ShiftTranslationDto.builder()
                                    .languageCode(t.getLanguageCode())
                                    .name(t.getName())
                                    .build())
                            .collect(Collectors.toList());
                    String shiftName = getShiftNameFromTranslations(translations, preferredLanguage, defaultLanguage);
                    
                    return ShiftResponse.builder()
                            .id(shift.getId())
                            .shiftName(shiftName)
                            .translations(translationDtos)
                            .startTime(shift.getStartTime())
                            .endTime(shift.getEndTime())
                            .status(shift.getStatus())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Applies in-memory sorting and pagination to shift responses
     */
    private List<ShiftResponse> applyInMemorySortingAndPagination(List<ShiftResponse> shiftResponses, 
            Sort.Direction direction, int pageNumber, int pageSize) {
        Comparator<ShiftResponse> comparator = Comparator.comparing(ShiftResponse::getShiftName, 
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        
        if (direction == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        
        shiftResponses.sort(comparator);
        
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, shiftResponses.size());
        return start < shiftResponses.size() ? shiftResponses.subList(start, end) : new ArrayList<>();
    }
    
    /**
     * Builds pagination metadata
     */
    private PaginationMetaData buildPaginationMetaData(int pageNumber, int pageSize, long totalCount) {
        int currentPage = pageNumber + 1;
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        return PaginationMetaData.builder()
                .page(currentPage)
                .size(pageSize)
                .totalPages(totalPages)
                .totalRecords(totalCount)
                .build();
    }
    
    /**
     * Helper class for pagination parameters
     */
    private static class PaginationParams {
        private final int pageNumber;
        private final int pageSize;
        
        PaginationParams(int pageNumber, int pageSize) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }
        
        int getPageNumber() {
            return pageNumber;
        }
        
        int getPageSize() {
            return pageSize;
        }
    }

    /**
     * Retrieves a single shift by its ID with all translations, using the preferred locale
     * for the shift name display.
     *
     * @param shiftId the UUID of the shift to retrieve
     * @param locale  optional locale for localized shift name
     * @return {@link ResponseDto} containing the shift data with translations
     * @throws ResponseStatusException if shift is not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ShiftDataResponse> getShiftById(UUID shiftId, String locale) {
        // Get locale from parameter or LocaleContextHolder
        String localeString = getLocaleString(locale);
        Locale userLocale = Locale.forLanguageTag(localeString);
        String preferredLanguage = localeString;
        String defaultLanguage = getDefaultLanguage();

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MESSAGE_KEY_SHIFT_NOT_FOUND, userLocale)
                ));

        // Fetch translations for this shift
        List<ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shiftId);

        // Build response with translations
        return buildShiftDataResponse(shift, translations, preferredLanguage, defaultLanguage, userLocale, "shift.fetch.success");
    }

    /**
     * Updates an existing shift's time, status, and translations. Validates that new
     * shift names are unique per language (excluding the current shift). Updates existing
     * translations or creates new ones, and removes translations not present in the request.
     *
     * @param shiftId  the UUID of the shift to update
     * @param request  the shift update request containing new time, status, and translations
     * @return {@link ResponseDto} containing the updated shift data with translations
     * @throws ResponseStatusException if shift is not found, translations are missing, or name conflicts exist
     */
    @Override
    @Transactional
    public ResponseDto<ShiftDataResponse> updateShift(UUID shiftId, ShiftRequest request) {
        Locale userLocale = LocaleContextHolder.getLocale();
        String defaultLanguage = getDefaultLanguage();

        Shift shift = findShiftById(shiftId, userLocale);
        validateTranslations(request, shiftId, userLocale);
        updateShiftEntity(shift, request);
        List<ShiftTranslation> translations = updateShiftTranslations(shift, shiftId, request);

        return buildShiftDataResponse(shift, translations, userLocale.getLanguage(), defaultLanguage, userLocale, "shift.update.success");
    }
    
    /**
     * Finds a shift by ID or throws exception
     */
    private Shift findShiftById(UUID shiftId, Locale userLocale) {
        return shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MESSAGE_KEY_SHIFT_NOT_FOUND, userLocale)
                ));
    }

    /**
     * Validates that translations are provided and don't conflict with existing ones
     */
    private void validateTranslations(ShiftRequest request, UUID shiftId, Locale userLocale) {
        if (request.getTranslations() == null || request.getTranslations().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("shift.create.error.translations.required", userLocale)
            );
        }

        for (ShiftTranslationDto translationDto : request.getTranslations()) {
            if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                boolean exists = shiftTranslationRepository.existsByNameAndLanguageCodeAndShiftIdNot(
                        translationDto.getName().trim(), translationDto.getLanguageCode(), shiftId);
                if (exists) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            messageUtil.getMessage("shift.create.error.exists", userLocale)
                    );
                }
                }
            }
        }

    /**
     * Updates the shift entity with new values
     */
    private void updateShiftEntity(Shift shift, ShiftRequest request) {
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setStatus(request.getStatus());
        shiftRepository.save(shift);
    }
    
    /**
     * Updates shift translations - removes old ones, updates existing, creates new ones
     */
    private List<ShiftTranslation> updateShiftTranslations(Shift shift, UUID shiftId, ShiftRequest request) {
        List<ShiftTranslation> existingTranslations = shiftTranslationRepository.findAllByShiftId(shiftId);
        Map<String, ShiftTranslation> existingTranslationMap = buildTranslationMap(existingTranslations);
        Set<String> validRequestLanguageCodes = extractValidLanguageCodes(request);
        
        removeObsoleteTranslations(existingTranslations, validRequestLanguageCodes);
        return saveOrUpdateTranslations(shift, request, existingTranslationMap);
    }
    
    /**
     * Builds a map of language code to translation for quick lookup
     */
    private Map<String, ShiftTranslation> buildTranslationMap(List<ShiftTranslation> translations) {
        Map<String, ShiftTranslation> map = new HashMap<>();
        for (ShiftTranslation translation : translations) {
            map.put(translation.getLanguageCode(), translation);
        }
        return map;
        }

    /**
     * Extracts valid language codes from request translations
     */
    private Set<String> extractValidLanguageCodes(ShiftRequest request) {
        return request.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && 
                           t.getName() != null && 
                           !t.getName().trim().isEmpty())
                .map(ShiftTranslationDto::getLanguageCode)
                .collect(Collectors.toSet());
    }
    
    /**
     * Removes translations that are no longer in the request
     */
    private void removeObsoleteTranslations(List<ShiftTranslation> existingTranslations, Set<String> validLanguageCodes) {
        List<ShiftTranslation> translationsToRemove = existingTranslations.stream()
                .filter(t -> !validLanguageCodes.contains(t.getLanguageCode()))
                .collect(Collectors.toList());
        shiftTranslationRepository.deleteAll(translationsToRemove);
    }
    
    /**
     * Saves or updates translations based on request
     */
    private List<ShiftTranslation> saveOrUpdateTranslations(Shift shift, ShiftRequest request, 
                                                           Map<String, ShiftTranslation> existingTranslationMap) {
        List<ShiftTranslation> translations = new ArrayList<>();
        for (ShiftTranslationDto translationDto : request.getTranslations()) {
            if (translationDto.getLanguageCode() != null && 
                translationDto.getName() != null && 
                !translationDto.getName().trim().isEmpty()) {
                
                ShiftTranslation translation = existingTranslationMap.get(translationDto.getLanguageCode());
                if (translation != null) {
                    translation.setName(translationDto.getName().trim());
                    shiftTranslationRepository.save(translation);
                    translations.add(translation);
                } else {
                    translation = createNewTranslation(shift, translationDto);
                    translations.add(translation);
                }
            }
        }
        return translations;
    }
    
    /**
     * Creates a new translation entity
     */
    private ShiftTranslation createNewTranslation(Shift shift, ShiftTranslationDto translationDto) {
        ShiftTranslation translation = ShiftTranslation.builder()
                .shift(shift)
                .languageCode(translationDto.getLanguageCode())
                .name(translationDto.getName().trim())
                .build();
        return shiftTranslationRepository.save(translation);
    }

    /**
     * Soft-deletes a shift by setting isDeleted to true. Validates that the shift exists,
     * is not already deleted, and is not assigned to any users before deletion.
     *
     * @param shiftId the UUID of the shift to delete
     * @param userId  the UUID of the user performing the deletion (for audit trail)
     * @return {@link ResponseDto} with a success message
     * @throws ResponseStatusException if shift is not found, already deleted, or assigned to users
     */
    @Override
    @Transactional
    public ResponseDto<Void> deleteShift(UUID shiftId, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MESSAGE_KEY_SHIFT_NOT_FOUND, userLocale)
                ));

        // Check if shift is already deleted
        if (Boolean.TRUE.equals(shift.getIsDeleted())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.delete.error.alreadydeleted", userLocale)
            );
        }

        // Check if shift is assigned to any user
        if (userShiftMappingRepository.existsByShiftId(shiftId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    messageUtil.getMessage("shift.delete.error.assigned.to.user", userLocale)
            );
        }

        // Find user for updatedBy
        User updater = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale, userId)));

        // Soft delete: set isDeleted to true
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        shift.setIsDeleted(true);
        shift.setUpdatedAt(now);
        shift.setUpdatedBy(updater);

        shiftRepository.save(shift);

        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("shift.delete.success", userLocale))
                .build();
    }
    
    /**
     * Helper method to build a ResponseDto with ShiftDataResponse from shift and translations.
     * 
     * @param shift The shift entity
     * @param translations List of shift translations
     * @param preferredLanguage The preferred language code for shift name
     * @param defaultLanguage The default language code as fallback
     * @param messageLocale The locale for the response message
     * @param messageKey The message key for the response
     * @return ResponseDto containing ShiftDataResponse
     */
    private ResponseDto<ShiftDataResponse> buildShiftDataResponse(
            Shift shift,
            List<ShiftTranslation> translations,
            String preferredLanguage,
            String defaultLanguage,
            Locale messageLocale,
            String messageKey) {
        
        // Build translation DTOs
        List<ShiftTranslationDto> translationDtos = translations.stream()
                .map(t -> ShiftTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
        
        String shiftName = getShiftNameFromTranslations(translations, preferredLanguage, defaultLanguage);
    
        ShiftResponse shiftResponse = ShiftResponse.builder()
                .id(shift.getId())
                .shiftName(shiftName)
                .translations(translationDtos)
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .status(shift.getStatus())
                .build();
    
        ShiftDataResponse data = ShiftDataResponse.builder()
                .shift(shiftResponse)
                .count(1L)
                .total(1L)
                .build();
    
        return ResponseDto.<ShiftDataResponse>builder()
                .message(messageUtil.getMessage(messageKey, messageLocale))
                .data(data)
                .build();
    }

    /**
     * Helper method to get the default language from localization properties
     * @return The default language code, or "en" if not configured
     */
    private String getDefaultLanguage() {
        return localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty()
                ? localizationProperties.getLanguages().get(0) : "en";
    }

    /**
     * Helper method to get locale string from parameter or LocaleContextHolder
     * @param locale The locale string parameter (can be null)
     * @return The locale string, defaulting to "en" if not provided
     */
    private String getLocaleString(String locale) {
        if (locale != null && !locale.trim().isEmpty()) {
            return locale.toLowerCase();
        }
        Locale contextLocale = LocaleContextHolder.getLocale();
        return contextLocale != null ? contextLocale.getLanguage() : "en";
    }

    /**
     * Helper method to get shift name from translations based on locale
     */
    private String getShiftNameFromTranslations(List<ShiftTranslation> translations, String preferredLocale, String defaultLanguage) {
        if (translations == null || translations.isEmpty()) {
            return "";
        }
        
        Optional<ShiftTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                translations,
                preferredLocale,
                localizationProperties.getLanguages(),
                ShiftTranslation::getLanguageCode
        );
        
        if (translation.isPresent()) {
            return translation.get().getName();
        }
        
        // Fallback to default language
        if (defaultLanguage != null) {
            Optional<ShiftTranslation> defaultTranslation = translations.stream()
                    .filter(t -> defaultLanguage.equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst();
            if (defaultTranslation.isPresent()) {
                return defaultTranslation.get().getName();
            }
        }
        
        // Last resort: return first available translation
        return translations.get(0).getName();
    }

    /**
     * Restores multiple soft-deleted shifts by setting isDeleted to false. Only shifts
     * that are currently deleted will be restored. Validates that at least one deleted
     * shift exists in the provided list.
     *
     * @param ids    list of shift UUIDs to restore
     * @param userId the UUID of the user performing the restoration (for audit trail)
     * @return {@link ResponseDto} with a success message
     * @throws ResponseStatusException if no shifts are found or none are in deleted state
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreShifts(List<UUID> ids, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User updater = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale, userId)));
        
        // Find all shifts by IDs
        List<Shift> shifts = shiftRepository.findAllById(ids);
        
        if (shifts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MESSAGE_KEY_SHIFT_NOT_FOUND, userLocale));
        }
        
        // Filter only deleted shifts and restore them
        List<Shift> deletedShifts = shifts.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedShifts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted shifts
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Shift shift : deletedShifts) {
            shift.setIsDeleted(false);
            shift.setUpdatedAt(now);
            shift.setUpdatedBy(updater);
        }
        
        shiftRepository.saveAll(deletedShifts);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("shift.restore.success", userLocale))
            .build();
    }
    
}
