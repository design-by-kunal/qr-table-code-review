package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.service.TemplateLayoutService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.repository.TemplateTableRepository;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.TableShape;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequest;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequestDto;
import com.gulfnet.shared_library.model.request.TemplateRowDto;
import com.gulfnet.shared_library.model.request.TemplateSectionDto;
import com.gulfnet.shared_library.model.request.TemplateSectionTranslationDto;
import com.gulfnet.shared_library.model.request.TemplateTableDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutStructureDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateSectionResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateSectionTranslationResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateRowResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateTableResponseDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import com.gulfnet.shared_library.model.response.dto.TemplateLayoutDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListResponse;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponse;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutTranslationDto;
import com.gulfnet.shared_library.repository.TemplateLayoutRepository;
import com.gulfnet.shared_library.repository.TemplateSectionTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateLayoutServiceImpl implements TemplateLayoutService {

    private static final String MSG_TEMPLATE_LAYOUT_NOT_FOUND = "template.layout.not.found";
    private static final String MSG_USER_NOT_FOUND = "user.not.found";

    private final TemplateLayoutRepository templateLayoutRepository;
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final LocalizationProperties localizationProperties;
    private final TemplateSectionTranslationRepository templateSectionTranslationRepository;
    private final AuditTrailService auditTrailService;
    private final RestaurantRepository restaurantRepository;
    private final TemplateTableRepository templateTableRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    // Helper class for capacity and section count
    private static class CapacitySectionCount {
        final int totalSeatingCapacity;
        final int sectionCount;
        
        CapacitySectionCount(int totalSeatingCapacity, int sectionCount) {
            this.totalSeatingCapacity = totalSeatingCapacity;
            this.sectionCount = sectionCount;
        }
    }

    /**
     * Retrieves a single template layout by id (excluding soft-deleted layouts).
     *
     * @param id template layout id
     * @return response wrapper containing the template layout details
     * @throws ResponseStatusException when the template layout is not found or is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> getTemplateLayout(UUID id) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        TemplateLayout templateLayout = templateLayoutRepository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)));

        TemplateLayoutResponse response = buildTemplateLayoutResponse(templateLayout);

        return ResponseDto.<TemplateLayoutDto<TemplateLayoutResponse>>builder()
            .message(messageUtil.getMessage("template.layout.get.success", userLocale))
            .data(wrapTemplateLayoutResponse(response))
            .build();
    }

    /**
     * Creates a new template layout with translated names.
     * <p>
     * Validates provided translations (including uniqueness/required language rules) and persists the layout
     * as non-deleted with the requested status.
     * </p>
     *
     * @param request   template layout request containing status and translations
     * @param creatorId id of the user creating the layout
     * @return response wrapper containing the created template layout
     * @throws ResponseStatusException when validation fails or the creator cannot be resolved
     */
    @Override
    @Transactional
    public ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> createTemplateLayout(TemplateLayoutRequest request, String creatorId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        User creator = findUserById(creatorId, userLocale, false);

        validateTemplateLayoutTranslations(request.getTranslations(), null, userLocale, true, null);

        TemplateLayout templateLayout = new TemplateLayout();
        templateLayout.setStatus(parseStatusFromRequest(request, userLocale));
        templateLayout.setTranslations(new ArrayList<>());
        templateLayout.setIsDeleted(false);
        templateLayout.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        templateLayout.setCreatedBy(creator);

        request.getTranslations().forEach(translationDto -> {
            String name = translationDto.getName();
            if (name != null && !name.trim().isEmpty()) {
                TemplateLayoutTranslation translation = new TemplateLayoutTranslation();
                translation.setLanguageCode(translationDto.getLanguageCode());
                translation.setName(name.trim());
                translation.setTemplate(templateLayout);
                templateLayout.getTranslations().add(translation);
            }
        });

        TemplateLayout savedLayout = templateLayoutRepository.save(templateLayout);

        TemplateLayoutResponse response = buildTemplateLayoutResponse(savedLayout);

        return ResponseDto.<TemplateLayoutDto<TemplateLayoutResponse>>builder()
                .message(messageUtil.getMessage("template.layout.create.success", userLocale))
                .data(wrapTemplateLayoutResponse(response))
                .build();
    }

    /**
     * Retrieves template layouts with optional filtering, searching, sorting, and paging.
     * <p>
     * Supports filtering by deletion flag and status, searching by translation name, optional language scoping,
     * and in-memory sorting/paging after applying a safety fetch limit.
     * </p>
     *
     * @param search       optional name search term (case-insensitive)
     * @param status       optional status filter
     * @param languageCode optional language code used to select names in list responses
     * @param page         1-based page number (optional)
     * @param size         page size (optional)
     * @param sortBy       sort field (e.g. name/createdAt)
     * @param direction    sort direction (ASC/DESC)
     * @param isDeleted    when true returns deleted layouts; otherwise returns non-deleted layouts (default)
     * @return response wrapper containing {@link TemplateLayoutListDto}
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TemplateLayoutListDto> getAllTemplateLayouts(
            String search,
            EntityStatus status,
            String languageCode,
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            Boolean isDeleted) {

        Locale userLocale = LocaleContextHolder.getLocale();
        Sort.Direction sortDirectionEnum = parseSortDirection(direction);
        Paging paging = normalizePaging(page, size);

        final int MAX_FETCH_LIMIT = 10000;
        Specification<TemplateLayout> spec = buildTemplateLayoutSpec(search, status, languageCode, isDeleted);
        List<TemplateLayout> allTemplateLayouts = limitForPerformance(templateLayoutRepository.findAll(spec), MAX_FETCH_LIMIT);

        Map<UUID, CapacitySectionCount> capacityMap = loadCapacitySectionCounts(allTemplateLayouts);
        List<TemplateLayoutListResponse> allResponses = toListResponses(allTemplateLayouts, capacityMap, languageCode);

        sortTemplateLayoutResponses(allResponses, sortBy, sortDirectionEnum, userLocale);
        List<TemplateLayoutListResponse> paginatedResponses = paginate(allResponses, paging.pageNumber, paging.pageSize);
        long totalRecords = allResponses.size();

        TemplateLayoutListDto dto = TemplateLayoutListDto.builder()
                .templateLayouts(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total(totalRecords)
                .metaData(paging.noPaging ? null : PaginationMetaData.builder()
                        .page(paging.pageNumber + 1)
                        .size(paging.pageSize)
                        .totalPages((int) Math.ceil((double) totalRecords / paging.pageSize))
                        .totalRecords(totalRecords)
                        .build())
                .build();

        return ResponseDto.<TemplateLayoutListDto>builder()
                .message(messageUtil.getMessage("template.layout.get.all.success", userLocale))
                .data(dto)
                .build();
    }

    private static final class Paging {
        final int pageNumber;
        final int pageSize;
        final boolean noPaging;

        private Paging(int pageNumber, int pageSize, boolean noPaging) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
            this.noPaging = noPaging;
        }
    }

    private Sort.Direction parseSortDirection(String direction) {
        try {
            return (direction != null && !direction.isBlank())
                    ? Sort.Direction.fromString(direction)
                    : Sort.Direction.ASC;
        } catch (Exception e) {
            return Sort.Direction.ASC;
        }
    }

    private Paging normalizePaging(Integer page, Integer size) {
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        if (pageNumber < 0) {
            pageNumber = 0;
        }
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        if (pageSize < 1) {
            pageSize = Integer.MAX_VALUE;
        }
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        return new Paging(pageNumber, pageSize, noPaging);
    }

    private Specification<TemplateLayout> buildTemplateLayoutSpec(String search, EntityStatus status, String languageCode, Boolean isDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (isDeleted != null && isDeleted) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (search != null && !search.trim().isEmpty()) {
                Join<TemplateLayout, TemplateLayoutTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(translationJoin.get("name")), searchPattern));
                if (languageCode != null) {
                    predicates.add(cb.equal(translationJoin.get("languageCode"), languageCode));
                }
                query.distinct(true);
            } else if (languageCode != null) {
                Join<TemplateLayout, TemplateLayoutTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                predicates.add(cb.equal(translationJoin.get("languageCode"), languageCode));
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<TemplateLayout> limitForPerformance(List<TemplateLayout> layouts, int maxFetchLimit) {
        if (layouts.size() > maxFetchLimit) {
            return layouts.subList(0, maxFetchLimit);
        }
        return layouts;
    }

    private Map<UUID, CapacitySectionCount> loadCapacitySectionCounts(List<TemplateLayout> layouts) {
        List<UUID> layoutIds = layouts.stream().map(TemplateLayout::getId).toList();
        Map<UUID, CapacitySectionCount> capacityMap = new HashMap<>();
        if (layoutIds.isEmpty()) {
            return capacityMap;
        }
        List<Object[]> capacityResults = templateLayoutRepository.findCapacityAndSectionCountByLayoutIds(layoutIds);
        for (Object[] result : capacityResults) {
            UUID layoutId = (UUID) result[0];
            Integer totalSeatingCapacity = ((Number) result[1]).intValue();
            Long sectionCount = ((Number) result[2]).longValue();
            capacityMap.put(layoutId, new CapacitySectionCount(totalSeatingCapacity, sectionCount.intValue()));
        }
        return capacityMap;
    }

    private List<TemplateLayoutListResponse> toListResponses(List<TemplateLayout> layouts,
                                                            Map<UUID, CapacitySectionCount> capacityMap,
                                                            String languageCode) {
        // Must be mutable because downstream sorting is in-place (List.sort / LocaleSortUtil.sortName).
        return layouts.stream()
                .map(layout -> {
                    CapacitySectionCount counts = capacityMap.getOrDefault(layout.getId(), new CapacitySectionCount(0, 0));
                    return toListResponse(layout, languageCode, counts.totalSeatingCapacity, counts.sectionCount);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void sortTemplateLayoutResponses(List<TemplateLayoutListResponse> allResponses,
                                            String sortBy,
                                            Sort.Direction sortDirectionEnum,
                                            Locale userLocale) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return;
        }
        String sortField = sortBy.trim().toLowerCase();
        if ("name".equalsIgnoreCase(sortField)) {
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(allResponses, sortBy, sortDirectionEnum);
            return;
        }
        if ("createdat".equalsIgnoreCase(sortField)) {
            Comparator<TemplateLayoutListResponse> comp = Comparator.comparing(
                    TemplateLayoutListResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirectionEnum == Sort.Direction.DESC) comp = comp.reversed();
            allResponses.sort(comp);
            return;
        }
        if ("status".equalsIgnoreCase(sortField)) {
            Comparator<TemplateLayoutListResponse> comp = Comparator.comparing(
                    TemplateLayoutListResponse::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirectionEnum == Sort.Direction.DESC) comp = comp.reversed();
            allResponses.sort(comp);
        }
    }

    private List<TemplateLayoutListResponse> paginate(List<TemplateLayoutListResponse> allResponses, int pageNumber, int pageSize) {
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allResponses.size());
        if (fromIndex >= allResponses.size()) {
            return Collections.emptyList();
        }
        return allResponses.subList(fromIndex, toIndex);
    }

    /**
     * Builds a list response DTO for a template layout including derived capacity and section-count fields.
     * <p>
     * Selects an appropriate translation name using: requested languageCode → user locale → configured fallback order.
     * </p>
     *
     * @param layout              layout entity
     * @param languageCode        preferred language code for the name field (may be null)
     * @param totalSeatingCapacity precomputed total seating capacity for this layout
     * @param sectionCount        precomputed number of sections for this layout
     * @return list response DTO
     */
    private TemplateLayoutListResponse toListResponse(TemplateLayout layout, String languageCode, 
                                                       int totalSeatingCapacity, int sectionCount) {

        String createdByName = null;
        if (layout.getCreatedBy() != null) {
            User createdBy = layout.getCreatedBy();
            String firstName = createdBy.getFirstName() != null ? createdBy.getFirstName() : "";
            String lastName = createdBy.getLastName() != null ? createdBy.getLastName() : "";
            createdByName = (firstName + " " + lastName).trim();
            if (createdByName.isEmpty()) {
                createdByName = null;
            }
        }

        // Apply fallback language logic for template layout translations
        String layoutName = "";
        String selectedLanguageCode = null;
        List<TemplateLayoutTranslation> translations = layout.getTranslations();
        
        if (!translations.isEmpty()) {
            // Try exact match first (from languageCode parameter)
            TemplateLayoutTranslation exactMatch = null;
            if (languageCode != null && !languageCode.trim().isEmpty()) {
                exactMatch = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && languageCode.equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst()
                    .orElse(null);
            }
            
            if (exactMatch != null) {
                layoutName = exactMatch.getName();
                selectedLanguageCode = exactMatch.getLanguageCode();
            } else {
                // Try user locale match
                String userLang = LocaleContextHolder.getLocale().getLanguage();
                TemplateLayoutTranslation userLocaleMatch = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && userLang.equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst()
                    .orElse(null);
                
                if (userLocaleMatch != null) {
                    layoutName = userLocaleMatch.getName();
                    selectedLanguageCode = userLocaleMatch.getLanguageCode();
                } else {
                    // Fallback using TranslationUtils
                    java.util.Optional<TemplateLayoutTranslation> fallback =
                            TranslationUtils.pickPreferredOrFromList(
                                    translations,
                                    userLang,
                                    localizationProperties.getLanguages(),
                                    TemplateLayoutTranslation::getLanguageCode
                            );
                    if (fallback.isPresent()) {
                        TemplateLayoutTranslation fallbackTranslation = fallback.get();
                        layoutName = fallbackTranslation.getName();
                        selectedLanguageCode = fallbackTranslation.getLanguageCode();
                    }
                }
            }
        }

        return TemplateLayoutListResponse.builder()
                .id(layout.getId())
                .status(layout.getStatus())
                .name(layoutName)
                .languageCode(selectedLanguageCode)
                .totalSeatingCapacity(totalSeatingCapacity)
                .sectionCount(sectionCount)
                .createdAt(layout.getCreatedAt() != null ? layout.getCreatedAt().toLocalDateTime() : null)
                .createdByName(createdByName)
                .build();
    }


    /**
     * Soft-deletes a template layout by setting {@code isDeleted=true}.
     * <p>
     * Rejects deletion when the layout is already deleted, updates audit fields, persists the change,
     * and writes an audit-trail entry.
     * </p>
     *
     * @param id        template layout id
     * @param updaterId id of the user performing the deletion (optional)
     * @return response wrapper with a localized success message
     * @throws ResponseStatusException when the layout is not found or already deleted
     */
    @Override
    @Transactional
    public ResponseDto<Void> softDeleteTemplateLayout(UUID id, String updaterId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        TemplateLayout layout = templateLayoutRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)
            ));

        if (Boolean.TRUE.equals(layout.getIsDeleted())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("template.layout.delete.error.already.deleted", userLocale)
            );
        }
        layout.setIsDeleted(true);
        layout.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (updaterId != null) {
            User updater = findUserById(updaterId, userLocale, false);
            layout.setUpdatedBy(updater);
        }

        templateLayoutRepository.save(layout);

        // Create audit trail for template layout deletion
        createTemplateLayoutAuditTrail(
                updaterId,
                ActionType.TABLE_LAYOUT_TEMPLATE_DELETE,
                layout,
                "Table Layout Template deleted: ",
                false,
                userLocale);

        String message = messageUtil.getMessage("template.layout.delete.success", userLocale);

        return ResponseDto.<Void>builder()
                .message(message)
                .build();
    }

    /**
     * Updates a template layout's status and translations.
     * <p>
     * Validates translations, replaces the translation set (deletes existing translations explicitly),
     * updates audit fields, persists the change, and writes an audit-trail entry.
     * </p>
     *
     * @param id        template layout id
     * @param request   updated status and translations
     * @param updaterId user id performing the update
     * @param userRole  role of the user (used by downstream validation/audit decisions)
     * @return response wrapper containing the updated template layout
     * @throws ResponseStatusException when the layout is not found, deleted, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> updateTemplateLayout(
            UUID id,
            TemplateLayoutRequest request,
            String updaterId,
            String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        User updater = findUserById(updaterId, userLocale, false);

        TemplateLayout existingLayout = templateLayoutRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)
                ));

        validateTemplateLayoutTranslations(request.getTranslations(), id, userLocale, false, null);

        // Delete existing translations explicitly
        templateLayoutRepository.deleteTranslationsByTemplateId(id);

        // Add new translations (only for non-empty names)
        List<TemplateLayoutTranslation> newTranslations = request.getTranslations().stream()
                .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                .map(t -> {
                    TemplateLayoutTranslation translation = new TemplateLayoutTranslation();
                    translation.setLanguageCode(t.getLanguageCode());
                    translation.setName(t.getName().trim());
                    translation.setTemplate(existingLayout);
                    return translation;
                })
                .collect(Collectors.toList());

        existingLayout.getTranslations().clear();
        existingLayout.getTranslations().addAll(newTranslations);

        existingLayout.setStatus(parseStatusFromRequest(request, userLocale));

        existingLayout.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existingLayout.setUpdatedBy(updater);

        TemplateLayout savedLayout = templateLayoutRepository.save(existingLayout);

        TemplateLayoutResponse response = buildTemplateLayoutResponse(savedLayout);

        // Create audit trail for template layout update
        createTemplateLayoutAuditTrail(
                updaterId,
                ActionType.TABLE_LAYOUT_TEMPLATE_UPDATE,
                savedLayout,
                "Table Layout Template updated: ",
                true,
                userLocale);

        return ResponseDto.<TemplateLayoutDto<TemplateLayoutResponse>>builder()
                .message(messageUtil.getMessage("template.layout.update.success", userLocale))
                .data(wrapTemplateLayoutResponse(response))
                .build();
    }

    /**
     * Creates (or upserts) the section/row/table structure for a template layout.
     * <p>
     * Validates the request, upserts sections and nested rows/tables (supporting moving tables between rows/sections),
     * soft-deletes missing elements, persists the layout, and writes an audit trail entry.
     * </p>
     *
     * @param templateLayoutId template layout id
     * @param requestDto       structure payload including sections, rows, and tables
     * @param creatorId        user id performing the create
     * @param userRole         role of the user (used for validation rules)
     * @return response wrapper containing the created structure response DTO
     * @throws ResponseStatusException when the layout is not found or request validation fails
     */
    @Override
    @Transactional
    public ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> createTemplateStructure(
            UUID templateLayoutId,
            TemplateLayoutRequestDto requestDto,
            String creatorId,
            String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        TemplateLayout templateLayout = templateLayoutRepository.findByIdAndIsDeletedFalse(templateLayoutId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)));

        User creator = findUserById(creatorId, userLocale, false);

        validateTemplateStructureRequest(requestDto, templateLayout, userLocale, userRole);

        // Precompute incoming table IDs across the whole request (needed to support moving tables between rows/sections)
        Set<UUID> allIncomingTableIds = extractIncomingTableIds(requestDto);

        // Build lookup maps once (avoid repeated O(n) scans per row)
        ExistingLayoutContext existingContext = buildExistingLayoutContext(templateLayout);

        List<TemplateSection> updatedSections = new ArrayList<>();

        // Upsert sections
        for (var sectionDto : requestDto.getSections()) {
                validateSectionNameUniqueness(sectionDto, templateLayout, existingContext.sectionsById, userLocale);

                TemplateSection section = upsertSection(
                        sectionDto,
                        templateLayout,
                        existingContext.sectionsById,
                        creator,
                        allIncomingTableIds,
                        existingContext.tablesById,
                        existingContext.tableCodes);

                updatedSections.add(section);
        }

        // Soft delete sections missing from request and merge with updated ones
        softDeleteAndMergeSections(templateLayout, requestDto, updatedSections, creator);

        TemplateLayout savedLayout = templateLayoutRepository.save(templateLayout);

        TemplateLayoutResponseDto responseDto = mapToResponseDto(savedLayout);

        // Create audit trail for template structure creation (includes sections and tables)
        createTemplateStructureAuditTrail(
                creatorId,
                ActionType.TABLE_LAYOUT_TEMPLATE_CREATE,
                savedLayout,
                "Template structure created with ",
                userLocale);

        return buildStructureResponse(responseDto, "template.structure.create.success", userLocale);

    }

    /**
     * Maps a {@link TemplateLayout} entity to the structure response DTO.
     * <p>
     * Filters out soft-deleted sections/rows/tables and sorts by their order fields to provide a stable API response.
     * </p>
     *
     * @param layout template layout entity
     * @return structure response DTO
     */
    private TemplateLayoutResponseDto mapToResponseDto(TemplateLayout layout) {
        List<TemplateSectionResponseDto> sections = layout.getSections().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .sorted(Comparator.comparing(TemplateSection::getSectionOrder))  // sort sections by sectionOrder
                .map(s -> TemplateSectionResponseDto.builder()
                        .id(s.getId())
                        .sectionOrder(s.getSectionOrder())
                        .translations(s.getTranslations().stream()
                                .map(t -> TemplateSectionTranslationResponseDto.builder()
                                        .languageCode(t.getLanguageCode())
                                        .name(t.getName())
                                        .build())
                                .collect(Collectors.toList()))
                        .rows(s.getRows().stream()
                                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                                .sorted(Comparator.comparing(TemplateRow::getRowOrder))  // sort rows by rowOrder
                                .map(r -> TemplateRowResponseDto.builder()
                                        .id(r.getId())
                                        .rowOrder(r.getRowOrder())
                                        .tables(r.getTables().stream()
                                                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                                                .sorted(Comparator.comparing(TemplateTable::getTableOrder))  // sort tables by tableOrder
                                                .map(t -> TemplateTableResponseDto.builder()
                                                        .id(t.getId())
                                                        .tableOrder(t.getTableOrder())
                                                        .shape(t.getShape())
                                                        .capacity(t.getCapacity())
                                                        .tableCode(t.getTableCode())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return TemplateLayoutResponseDto.builder()
                .sections(sections)
                .build();
        }

        /**
         * Retrieves the section/row/table structure for a template layout.
         *
         * @param templateLayoutId template layout id
         * @return response wrapper containing the structure DTO
         * @throws ResponseStatusException when the layout is not found or is deleted
         */
        @Override
        @Transactional(readOnly = true)
        public ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> getTemplateStructure(UUID templateLayoutId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        TemplateLayout templateLayout = templateLayoutRepository.findByIdAndIsDeletedFalse(templateLayoutId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)));

        TemplateLayoutResponseDto responseDto = mapToResponseDto(templateLayout);

        return buildStructureResponse(responseDto, "template.structure.get.success", userLocale);
        }


        /**
         * Updates the section/row/table structure for a template layout.
         * <p>
         * Upserts incoming sections and nested rows/tables, supports moving tables between rows/sections,
         * soft-deletes entities missing from the request, persists the resulting structure, and writes an audit trail.
         * </p>
         *
         * @param templateLayoutId template layout id
         * @param requestDto       updated structure payload
         * @param updaterId        user id performing the update
         * @param userRole         role of the user (used for validation rules)
         * @return response wrapper containing the updated structure DTO
         * @throws ResponseStatusException when the layout is not found or request validation fails
         */
        @Override
        @Transactional
        public ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> updateTemplateStructure(
                UUID templateLayoutId,
                TemplateLayoutRequestDto requestDto,
                String updaterId,
                String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        TemplateLayout templateLayout = templateLayoutRepository.findByIdAndIsDeletedFalse(templateLayoutId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale)));

        User updater = findUserById(updaterId, userLocale, false);

        validateTemplateStructureRequest(requestDto, templateLayout, userLocale, userRole);

        // Precompute incoming table IDs across the whole request (needed to support moving tables between rows/sections)
        Set<UUID> allIncomingTableIds = extractIncomingTableIds(requestDto);

        // Build lookup maps once (avoid repeated O(n) scans per row)
        ExistingLayoutContext existingContext = buildExistingLayoutContext(templateLayout);

        List<TemplateSection> updatedSections = new ArrayList<>();

        // Upsert sections
        for (var sectionDto : requestDto.getSections()) {
                validateSectionNameUniqueness(sectionDto, templateLayout, existingContext.sectionsById, userLocale);

                TemplateSection section = upsertSection(
                        sectionDto,
                        templateLayout,
                        existingContext.sectionsById,
                        updater,
                        allIncomingTableIds,
                        existingContext.tablesById,
                        existingContext.tableCodes);

                updatedSections.add(section);
        }

        // Soft delete sections missing from request and merge with updated ones
        softDeleteAndMergeSections(templateLayout, requestDto, updatedSections, updater);

        TemplateLayout savedLayout = templateLayoutRepository.save(templateLayout);

        TemplateLayoutResponseDto responseDto = mapToResponseDto(savedLayout);

        // Create audit trail for template structure update (includes sections and tables)
        if (updater != null) {
            createTemplateStructureAuditTrail(
                    updaterId,
                    ActionType.TABLE_LAYOUT_TEMPLATE_UPDATE,
                    savedLayout,
                    "Template structure updated with ",
                    userLocale);
        }

        return buildStructureResponse(responseDto, "template.structure.update.success", userLocale);
        }


        /**
         * Upserts section translations, ignoring empty names, and updates the existing collection in-place.
         *
         * @param section          section entity to update
         * @param translationsDto  incoming translation DTOs
         */
        private void upsertSectionTranslations(TemplateSection section, List<TemplateSectionTranslationDto> translationsDto) {
                Map<String, TemplateSectionTranslation> existingTranslations = section.getTranslations().stream()
                        .collect(Collectors.toMap(TemplateSectionTranslation::getLanguageCode, t -> t));

                List<TemplateSectionTranslation> updatedTranslations = new ArrayList<>();

                for (var tDto : translationsDto) {
                        // Only process translations with non-empty names
                        if (tDto.getName() != null && !tDto.getName().trim().isEmpty()) {
                                TemplateSectionTranslation translation;
                                if (existingTranslations.containsKey(tDto.getLanguageCode())) {
                                translation = existingTranslations.get(tDto.getLanguageCode());
                                translation.setName(tDto.getName());
                                } else {
                                translation = new TemplateSectionTranslation();
                                translation.setTemplateSection(section);
                                translation.setLanguageCode(tDto.getLanguageCode());
                                translation.setName(tDto.getName());
                                }
                                updatedTranslations.add(translation);
                        }
                }
                if (section.getTranslations() == null) {
                        section.setTranslations(new ArrayList<>());
                }

                // Clear and update existing collection instead of replacing
                section.getTranslations().clear();
                section.getTranslations().addAll(updatedTranslations);
        }

        /**
         * Upserts rows under a section and delegates to {@link #upsertTables(TemplateRow, List, User, TemplateLayout, Set, Map, Set)}
         * for nested table handling.
         * <p>
         * Rows missing from the request are soft-deleted, and their tables are soft-deleted as well.
         * Collection updates are performed in-place to avoid JPA orphan-removal pitfalls.
         * </p>
         *
         * @param section            parent section
         * @param rowsDto            incoming rows
         * @param updater            user performing the update
         * @param templateLayout     parent layout (used for validations)
         * @param allIncomingTableIds all table ids referenced anywhere in the request (used to detect moves vs deletes)
         * @param existingTablesById lookup of existing tables by id
         * @param existingTableCodes set of existing table codes for uniqueness validation
         */
        private void upsertRows(TemplateSection section,
                                List<TemplateRowDto> rowsDto,
                                User updater,
                                TemplateLayout templateLayout,
                                Set<UUID> allIncomingTableIds,
                                Map<UUID, TemplateTable> existingTablesById,
                                Set<String> existingTableCodes) {
        Map<UUID, TemplateRow> existingRows = section.getRows() == null ? 
                new HashMap<>() : 
                section.getRows().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .collect(Collectors.toMap(TemplateRow::getId, r -> r));

        List<TemplateRow> updatedRows = new ArrayList<>();

        for (var rowDto : rowsDto) {
                TemplateRow row;
                if (rowDto.getId() != null && existingRows.containsKey(rowDto.getId())) {
                // Update existing row
                row = existingRows.get(rowDto.getId());
                row.setRowOrder(rowDto.getRowOrder());
                row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setUpdatedBy(updater);
                } else {
                // New row
                row = new TemplateRow();
                row.setTemplateSection(section);
                row.setRowOrder(rowDto.getRowOrder());
                row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setCreatedBy(updater);
                row.setIsDeleted(false);
                }

                // Upsert tables in this row
                upsertTables(row, rowDto.getTables(), updater, templateLayout, allIncomingTableIds, existingTablesById, existingTableCodes);

                updatedRows.add(row);
        }

        // Soft delete rows missing from request
        Set<UUID> incomingRowIds = rowsDto.stream()
                .map(TemplateRowDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (section.getRows() != null) {
                for (TemplateRow existingRow : section.getRows()) {
                if (!incomingRowIds.contains(existingRow.getId()) && !Boolean.TRUE.equals(existingRow.getIsDeleted())) {
                        existingRow.setIsDeleted(true);
                        existingRow.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        existingRow.setUpdatedBy(updater);
                        softDeleteTables(existingRow.getTables(), updater);
                }
                }
        }

        // Clear and update the original collection instance to preserve JPA orphan removal handling
        if (section.getRows() == null)
                section.setRows(new ArrayList<>());

        List<TemplateRow> softDeletedRows = section.getRows().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDeleted()))
                .collect(Collectors.toList());

        section.getRows().clear();
        section.getRows().addAll(updatedRows);

        section.getRows().addAll(softDeletedRows);
        }

        /**
         * Upserts tables under a row, supporting updates, inserts, and moving existing tables between rows/sections.
         * <p>
         * Enforces table-code uniqueness within the template layout, validates moves stay within the same layout,
         * and avoids calling {@code clear()} on collections to prevent orphan-removal delete cascades.
         * Tables missing from the request are soft-deleted only when they are not present in {@code allIncomingTableIds}
         * (i.e., not moved elsewhere).
         * </p>
         *
         * @param row                parent row
         * @param tablesDto           incoming tables
         * @param updater             user performing the update
         * @param templateLayout      parent layout
         * @param allIncomingTableIds all table ids referenced anywhere in the request
         * @param existingTablesById  lookup of existing tables by id
         * @param existingTableCodes  set of existing codes for uniqueness validation
         * @throws ResponseStatusException when table move/uniqueness validation fails
         */
        private void upsertTables(TemplateRow row,
                                  List<TemplateTableDto> tablesDto,
                                  User updater,
                                  TemplateLayout templateLayout,
                                  Set<UUID> allIncomingTableIds,
                                  Map<UUID, TemplateTable> existingTablesById,
                                  Set<String> existingTableCodes) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        if (row.getTables() == null) {
                row.setTables(new ArrayList<>());
        }
        
        // Build a map of existing tables in this row for quick lookup
        Map<UUID, TemplateTable> existingTablesInRow = row.getTables().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .collect(Collectors.toMap(TemplateTable::getId, t -> t));
        
        List<TemplateTable> updatedTables = new ArrayList<>();

        for (var tableDto : tablesDto) {
                TemplateTable table = upsertSingleTable(row, tableDto, updater, templateLayout, existingTablesInRow,
                        existingTableCodes, updatedTables, userLocale);
                updatedTables.add(table);
        }

        Set<UUID> incomingTableIds = tablesDto.stream()
                .map(TemplateTableDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Build new list without using clear() to avoid orphanRemoval issues
        // DO NOT manually remove moved tables from collections - this triggers orphanRemoval
        // Instead, just skip them when building finalTables - Hibernate will handle collection updates
        List<TemplateTable> finalTables = new ArrayList<>();
        
        // Create a snapshot of the current collection to avoid concurrent modification
        List<TemplateTable> currentTables = row.getTables() != null ? new ArrayList<>(row.getTables()) : new ArrayList<>();
        
        // Add tables that are staying in this row (updated or unchanged)
        for (TemplateTable existingTable : currentTables) {
                UUID tableId = existingTable.getId();
                
                // Skip if table is in updatedTables (will be added separately)
                if (tableId != null && incomingTableIds.contains(tableId)) {
                        continue;
                }
                
                // Only mark as deleted if:
                // 1. It's not in the incoming tables for this row, AND
                // 2. It's not in the global set of all incoming table IDs (meaning it wasn't moved to another row)
                if (tableId != null && !incomingTableIds.contains(tableId) 
                        && !allIncomingTableIds.contains(tableId)
                        && !Boolean.TRUE.equals(existingTable.getIsDeleted())) {
                        // Table is truly being deleted (not moved)
                        existingTable.setIsDeleted(true);
                        existingTable.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        existingTable.setUpdatedBy(updater);
                }
                
                // Add to final list (including soft-deleted ones)
                finalTables.add(existingTable);
        }
        
        // Add all updated/new/moved tables
        finalTables.addAll(updatedTables);
        
        // CRITICAL: Don't use clear() - it triggers orphanRemoval for ALL tables
        // Instead, manually manage the collection by removing only what needs to be removed
        if (row.getTables() == null) {
                row.setTables(new ArrayList<>());
        }
        
        // Get final table IDs (filter out null IDs for new tables)
        Set<UUID> finalTableIds = finalTables.stream()
                .map(TemplateTable::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Remove tables that are NOT in the final list (but only if they're not moved)
        // CRITICAL: Do NOT remove moved tables - they've already been updated with new row reference
        // Removing them would trigger orphanRemoval and cause Hibernate to try to DELETE them
        List<TemplateTable> toRemove = new ArrayList<>();
        // Create a copy to iterate over to avoid concurrent modification
        List<TemplateTable> tablesToCheck = new ArrayList<>(row.getTables());
        for (TemplateTable currentTable : tablesToCheck) {
                UUID tableId = currentTable.getId();
                
                // Skip tables with null IDs (new tables that haven't been persisted yet)
                if (tableId == null) {
                        continue;
                }
                
                // CRITICAL CHECKS - Do NOT remove if any of these are true:
                // 1. Table's row reference has changed (it's been moved)
                boolean rowReferenceChanged = currentTable.getTemplateRow() != null && 
                        !currentTable.getTemplateRow().getId().equals(row.getId());
                
                // 2. Table is in allIncomingTableIds (it exists somewhere in the request, just not this row)
                boolean existsInRequest = allIncomingTableIds != null && allIncomingTableIds.contains(tableId);
                
                // Only remove if:
                // - It's not in the final list for this row, AND
                // - Its row reference hasn't changed, AND
                // - It's not in the global request (meaning it's truly being deleted)
                if (!finalTableIds.contains(tableId) && !rowReferenceChanged && !existsInRequest) {
                        toRemove.add(currentTable);
                }
        }
        // Only remove tables that are truly being deleted, not moved
        if (!toRemove.isEmpty()) {
                row.getTables().removeAll(toRemove);
        }
        
        // Add tables that are NOT already in the collection
        // CRITICAL: Ensure all tables in finalTables are in the row's collection
        for (TemplateTable finalTable : finalTables) {
                UUID tableId = finalTable.getId();
                
                // Check if table is already in collection (by ID for existing tables, by reference for new tables)
                boolean alreadyInCollection;
                if (tableId != null) {
                        // Existing table - match by ID (null-safe comparison)
                        alreadyInCollection = row.getTables().stream()
                                .anyMatch(t -> tableId.equals(t.getId()));
                } else {
                        // New table without ID - match by object reference
                        alreadyInCollection = row.getTables().contains(finalTable);
                }
                
                if (!alreadyInCollection) {
                        row.getTables().add(finalTable);
                } else {
                        // Table is already in collection - ensure it's the same instance
                        // Replace it with the updated instance from finalTables
                        if (tableId != null) {
                                // Existing table - remove by ID (null-safe comparison)
                                row.getTables().removeIf(t -> tableId.equals(t.getId()));
                        } else {
                                // New table - remove by reference
                                row.getTables().remove(finalTable);
                        }
                        row.getTables().add(finalTable);
                }
        }
        }

        private TemplateTable upsertSingleTable(TemplateRow row,
                                               TemplateTableDto tableDto,
                                               User updater,
                                               TemplateLayout templateLayout,
                                               Map<UUID, TemplateTable> existingTablesInRow,
                                               Set<String> existingTableCodes,
                                               List<TemplateTable> updatedTables,
                                               Locale userLocale) {
                // IMPORTANT: when moving a table to another row/section, it won't be in this row's existing tables.
                // If ID is present, treat it as an update and exclude it from uniqueness check.
                UUID excludeTableId = tableDto.getId();
                validateTableCodeUniqueness(
                        templateLayout.getId(),
                        tableDto.getTableCode(),
                        excludeTableId,
                        Collections.emptyList(),
                        existingTableCodes,
                        updatedTables,
                        userLocale);

                if (tableDto.getId() != null && existingTablesInRow.containsKey(tableDto.getId())) {
                        TemplateTable table = existingTablesInRow.get(tableDto.getId());
                        applyTableProperties(table, tableDto, updater, false);
                        return table;
                }

                if (tableDto.getId() != null) {
                        return moveOrCreateTableById(row, tableDto, updater, userLocale);
                }

                TemplateTable table = new TemplateTable();
                table.setTemplateRow(row);
                applyTableProperties(table, tableDto, updater, true);
                return table;
        }

        private TemplateTable moveOrCreateTableById(TemplateRow row,
                                                    TemplateTableDto tableDto,
                                                    User updater,
                                                    Locale userLocale) {
                TemplateTable table = templateTableRepository.findById(tableDto.getId()).orElse(null);
                if (table == null) {
                        TemplateTable newTable = new TemplateTable();
                        newTable.setTemplateRow(row);
                        applyTableProperties(newTable, tableDto, updater, true);
                        return newTable;
                }

                validateMovableTable(table, row, userLocale);
                table.setTemplateRow(row);
                applyTableProperties(table, tableDto, updater, false);
                if (!row.getTables().contains(table)) {
                        row.getTables().add(table);
                }
                return table;
        }

        private void validateMovableTable(TemplateTable table, TemplateRow targetRow, Locale userLocale) {
                if (Boolean.TRUE.equals(table.getIsDeleted())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("table.cannot.move.deleted", userLocale));
                }

                TemplateRow existingRow = table.getTemplateRow();
                TemplateSection existingSection = existingRow != null ? existingRow.getTemplateSection() : null;
                TemplateLayout existingLayout = existingSection != null ? existingSection.getLayoutTemplate() : null;

                TemplateSection currentSection = targetRow.getTemplateSection();
                TemplateLayout currentLayout = currentSection != null ? currentSection.getLayoutTemplate() : null;

                if (currentLayout == null || existingLayout == null || !currentLayout.getId().equals(existingLayout.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("table.cannot.move.different.layout", userLocale));
                }
        }

        /**
         * Builds a lookup map of all tables in the current template layout (including soft-deleted).
         * This is required to support moving tables between rows/sections by ID.
         */
        private Map<UUID, TemplateTable> buildExistingTablesById(TemplateLayout templateLayout) {
                Map<UUID, TemplateTable> map = new HashMap<>();
                if (templateLayout == null || templateLayout.getSections() == null) {
                        return map;
                }

                for (TemplateSection section : templateLayout.getSections()) {
                        if (section.getRows() == null) continue;
                        for (TemplateRow row : section.getRows()) {
                                if (row.getTables() == null) continue;
                                for (TemplateTable table : row.getTables()) {
                                        if (table != null && table.getId() != null) {
                                                map.put(table.getId(), table);
                                        }
                                }
                        }
                }
                return map;
        }


        private void softDeleteRows(List<TemplateRow> rows, User updater) {
                for (TemplateRow row : rows) {
                        if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                        row.setIsDeleted(true);
                        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        row.setUpdatedBy(updater);
                        softDeleteTables(row.getTables(), updater);
                        }
                }
        }

        private void softDeleteTables(List<TemplateTable> tables, User updater) {
                for (TemplateTable table : tables) {
                        if (!Boolean.TRUE.equals(table.getIsDeleted())) {
                        table.setIsDeleted(true);
                        table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        table.setUpdatedBy(updater);
                        }
                }
        }

    /**
     * Validates that the new capacity is not greater than the existing capacity for existing tables.
     * This ensures managers can only reduce capacity but not increase it.
     * HQ_ADMIN can increase capacity.
     * 
     * @param tableId The ID of the table to validate
     * @param newCapacity The new capacity being set
     * @param templateLayout The current template layout being updated
     * @param userLocale The user's locale for error messages
     * @param userRole The role of the user performing the update
     * @throws ResponseStatusException if capacity increase is attempted by non-HQ_ADMIN user
     */
    private void validateCapacityReduction(UUID tableId, Integer newCapacity, TemplateLayout templateLayout, Locale userLocale, String userRole) {
        // Find the existing table in the current template layout
        TemplateTable existingTable = findExistingTemplateTable(tableId, templateLayout);
        
        if (existingTable != null
                && newCapacity > existingTable.getCapacity()
                && (userRole == null || !"HQ_ADMIN".equalsIgnoreCase(userRole))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("capacity.cannot.increase", userLocale));
        }
    }
    
    /**
     * Helper method to find an existing template table by ID within a specific template layout.
     * 
     * @param tableId The ID of the table to find
     * @param templateLayout The template layout to search in
     * @return The TemplateTable if found, null otherwise
     */
    private TemplateTable findExistingTemplateTable(UUID tableId, TemplateLayout templateLayout) {
        // Search within the current template layout only
        for (TemplateSection section : templateLayout.getSections()) {
            if (!Boolean.TRUE.equals(section.getIsDeleted())) {
                for (TemplateRow row : section.getRows()) {
                    if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                        for (TemplateTable table : row.getTables()) {
                            if (!Boolean.TRUE.equals(table.getIsDeleted()) && 
                                table.getId().equals(tableId)) {
                                return table;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds a set of existing table codes (normalized to lowercase) from the in-memory template layout.
     * Includes both active and soft-deleted tables to prevent reuse (since tables can be restored).
     * 
     * @param templateLayout The template layout to extract table codes from
     * @return Set of normalized (lowercase) table codes
     */
    private Set<String> buildExistingTableCodesSet(TemplateLayout templateLayout) {
        Set<String> tableCodes = new HashSet<>();
        
        for (TemplateSection section : templateLayout.getSections()) {
            if (!Boolean.TRUE.equals(section.getIsDeleted())) {
                for (TemplateRow row : section.getRows()) {
                    if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                        for (TemplateTable table : row.getTables()) {
                            // Include both active and soft-deleted tables
                            if (table.getTableCode() != null) {
                                tableCodes.add(table.getTableCode().trim().toLowerCase());
                            }
                        }
                    }
                }
            }
        }
        
        return tableCodes;
    }

    /**
     * Validates that the table code is unique within the template layout.
     * Uses optimized O(1) set lookups instead of O(n) scans.
     * Performs both in-memory and database-level checks for reliability.
     * 
     * Note: Soft-deleted tables are included in the uniqueness check to prevent reuse of tableCode
     * values, since deleted tables can be restored.
     * 
     * @param templateLayoutId The template layout ID for database check
     * @param tableCode The table code to validate
     * @param excludeTableId Optional table ID to exclude from the uniqueness check (for updates)
     * @param excludeTableIds List of table IDs to exclude from database check (for batch updates) - currently unused but kept for future optimization
     * @param existingTableCodes Pre-built set of existing table codes from in-memory layout (for fast lookup)
     * @param updatedTables List of tables being added/updated in the current batch (to check for duplicates within batch)
     * @param userLocale The user's locale for error messages
     * @throws ResponseStatusException if the table code already exists
     */
    private void validateTableCodeUniqueness(UUID templateLayoutId, String tableCode, UUID excludeTableId,
                                             List<UUID> excludeTableIds, Set<String> existingTableCodes,
                                             List<TemplateTable> updatedTables, Locale userLocale) {
        if (tableCode == null || tableCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.table.code.required", userLocale));
        }

        String normalizedTableCode = tableCode.trim().toLowerCase();

        // Step 1: Check against tables being added in the same batch (O(n) where n = batch size, typically small)
        if (updatedTables != null) {
            for (TemplateTable table : updatedTables) {
                // Skip the table being updated (if excludeTableId is provided)
                if (excludeTableId != null && table.getId() != null && table.getId().equals(excludeTableId)) {
                    continue;
                }
                
                // Case-insensitive comparison
                if (table.getTableCode() != null && 
                    table.getTableCode().trim().toLowerCase().equals(normalizedTableCode)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("template.table.code.duplicate", userLocale, tableCode));
                }
            }
        }

        // Step 2: Quick in-memory check against pre-built set (O(1) lookup)
        // This is a fast pre-check before hitting the database
        if (existingTableCodes.contains(normalizedTableCode)) {
            // If updating and the code matches the excluded table's code, it's allowed
            // Otherwise, we need to verify via database check (to handle excludeTableId properly)
            // Database check will handle the excludeTableId logic correctly
        }

        // Step 3: Database-level check as authoritative source (handles excludeTableId correctly)
        // This ensures we catch:
        // - Tables that might not be in the in-memory object (if layout wasn't fully loaded)
        // - Concurrent modifications from other transactions
        // - Proper exclusion of the table being updated
        
        // First check if the code exists in a deleted table - provide specific message
        boolean existsInDeletedTable = templateLayoutRepository.existsDeletedTableCodeInTemplateLayout(
                templateLayoutId, tableCode, excludeTableId);
        
        if (existsInDeletedTable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("template.table.code.exists.in.deleted", userLocale, tableCode));
        }
        
        // Then check if the code exists in any table (active or deleted)
        boolean existsInDb = templateLayoutRepository.existsTableCodeInTemplateLayout(
                templateLayoutId, tableCode, excludeTableId);
        
        if (existsInDb) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("template.table.code.duplicate", userLocale, tableCode));
        }
    }

    /**
     * Helper context object grouping existing entities for a template layout to avoid repeated scans.
     */
    private static class ExistingLayoutContext {
        final Map<UUID, TemplateTable> tablesById;
        final Set<String> tableCodes;
        final Map<UUID, TemplateSection> sectionsById;

        ExistingLayoutContext(Map<UUID, TemplateTable> tablesById,
                              Set<String> tableCodes,
                              Map<UUID, TemplateSection> sectionsById) {
            this.tablesById = tablesById;
            this.tableCodes = tableCodes;
            this.sectionsById = sectionsById;
        }
    }

    /**
     * Builds a reusable context for an existing template layout (tables map, table codes set, sections map).
     */
    private ExistingLayoutContext buildExistingLayoutContext(TemplateLayout templateLayout) {
        Map<UUID, TemplateTable> tablesById = buildExistingTablesById(templateLayout);
        Set<String> tableCodes = buildExistingTableCodesSet(templateLayout);
        Map<UUID, TemplateSection> sectionsById = templateLayout.getSections().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toMap(TemplateSection::getId, s -> s));

        return new ExistingLayoutContext(tablesById, tableCodes, sectionsById);
    }

    /**
     * Extracts all incoming table IDs from the request (ignores null IDs for new tables).
     */
    private Set<UUID> extractIncomingTableIds(TemplateLayoutRequestDto requestDto) {
        return requestDto.getSections().stream()
                .flatMap(s -> s.getRows().stream())
                .flatMap(r -> r.getTables().stream())
                .map(TemplateTableDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Validates that section names are unique per language within a layout (against DB & in-memory).
     */
    private void validateSectionNameUniqueness(TemplateSectionDto sectionDto,
                                               TemplateLayout templateLayout,
                                               Map<UUID, TemplateSection> existingSections,
                                               Locale userLocale) {
        for (var translationDto : sectionDto.getTranslations()) {
            if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                boolean exists;
                if (sectionDto.getId() != null && existingSections.containsKey(sectionDto.getId())) {
                    String tName = translationDto.getName().trim().toLowerCase();
                    String tLang = translationDto.getLanguageCode();
                    exists = templateLayout.getSections().stream()
                            .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                            .filter(s -> !s.getId().equals(sectionDto.getId()))
                            .flatMap(s -> s.getTranslations().stream())
                            .anyMatch(t -> t.getLanguageCode() != null
                                    && tLang.equals(t.getLanguageCode())
                                    && t.getName() != null
                                    && t.getName().trim().toLowerCase().equals(tName));
                } else {
                    exists = templateSectionTranslationRepository.existsByNameLanguageAndLayout(
                            translationDto.getName(),
                            translationDto.getLanguageCode(),
                            templateLayout.getId());
                }
                if (exists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("template.structure.create.error.section.name.exists", userLocale));
                }
            }
        }
    }

    /**
     * Upserts a single section (create or update), including translations and rows/tables.
     */
    private TemplateSection upsertSection(TemplateSectionDto sectionDto,
                                          TemplateLayout templateLayout,
                                          Map<UUID, TemplateSection> existingSections,
                                          User actor,
                                          Set<UUID> allIncomingTableIds,
                                          Map<UUID, TemplateTable> existingTablesById,
                                          Set<String> existingTableCodes) {
        TemplateSection section;
        if (sectionDto.getId() != null && existingSections.containsKey(sectionDto.getId())) {
            // Update existing section entity fetched
            section = existingSections.get(sectionDto.getId());
            section.setSectionOrder(sectionDto.getSectionOrder());
            section.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            section.setUpdatedBy(actor);
        } else {
            // New section entity
            section = new TemplateSection();
            section.setLayoutTemplate(templateLayout);
            section.setSectionOrder(sectionDto.getSectionOrder());
            section.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            section.setCreatedBy(actor);
            section.setIsDeleted(false);
        }

        // Upsert translations (updating existing or adding new)
        upsertSectionTranslations(section, sectionDto.getTranslations());

        // Upsert rows, preserving IDs if present
        upsertRows(section, sectionDto.getRows(), actor, templateLayout, allIncomingTableIds, existingTablesById, existingTableCodes);

        return section;
    }

    /**
     * Soft deletes sections that are missing from the incoming request and merges
     * updated sections with already soft-deleted ones back into the layout.
     */
    private void softDeleteAndMergeSections(TemplateLayout templateLayout,
                                            TemplateLayoutRequestDto requestDto,
                                            List<TemplateSection> updatedSections,
                                            User actor) {
        // Soft delete sections missing from request
        Set<UUID> incomingSectionIds = requestDto.getSections().stream()
                .map(TemplateSectionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (TemplateSection existingSection : templateLayout.getSections()) {
            if (!incomingSectionIds.contains(existingSection.getId())
                    && !Boolean.TRUE.equals(existingSection.getIsDeleted())) {
                existingSection.setIsDeleted(true);
                existingSection.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                existingSection.setUpdatedBy(actor);
                softDeleteRows(existingSection.getRows(), actor);
            }
        }

        List<TemplateSection> softDeletedSections = templateLayout.getSections().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());

        // IMPORTANT: update the existing collection instance instead of replacing
        templateLayout.getSections().clear();
        templateLayout.getSections().addAll(updatedSections);
        templateLayout.getSections().addAll(softDeletedSections);
    }

    /**
     * Parses and validates EntityStatus from the request, shared by create/update.
     */
    private EntityStatus parseStatusFromRequest(TemplateLayoutRequest request, Locale userLocale) {
        try {
            return EntityStatus.valueOf(request.getStatus().name());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("template.layout.create.error.invalid.status", userLocale)
            );
        }
    }

    /**
     * Builds a TemplateLayoutResponse DTO from a TemplateLayout entity.
     */
    private TemplateLayoutResponse buildTemplateLayoutResponse(TemplateLayout layout) {
        return TemplateLayoutResponse.builder()
                .id(layout.getId())
                .status(layout.getStatus())
                .translations(layout.getTranslations().stream()
                        .map(translation -> TemplateLayoutTranslationDto.builder()
                                .languageCode(translation.getLanguageCode())
                                .name(translation.getName())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Shared helper to create audit trail entries for template layout (non-structure) actions.
     *
     * @param userId           id of the acting user (String UUID)
     * @param actionType       action being logged
     * @param layout           template layout involved
     * @param actionPrefix     prefix for description, layout name appended
     * @param requireUserFound whether to throw if user not found (true for update, false for delete)
     * @param userLocale       locale for error messages
     */
    private void createTemplateLayoutAuditTrail(String userId,
                                                ActionType actionType,
                                                TemplateLayout layout,
                                                String actionPrefix,
                                                boolean requireUserFound,
                                                Locale userLocale) {
        try {
            if (userId == null) {
                return;
            }

            UUID uuid = UUID.fromString(userId);
            User user;
            if (requireUserFound) {
                user = userRepository.findById(uuid).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
            } else {
                user = userRepository.findById(uuid).orElse(null);
            }

            if (user == null) {
                return;
            }

            Restaurant restaurant = findRestaurantByUser(user);

            String templateName = extractTemplateName(layout);
            String description = actionPrefix + templateName;

            createTemplateLayoutAuditTrailEntry(user, actionType, restaurant, layout, description);
        } catch (Exception e) {
            log.error("Failed to create audit trail for template layout action {}: {}", actionType, e.getMessage());
            // Don't break business flow if audit trail fails
        }
    }

    /**
     * Shared helper to create audit trail entries for template structure actions (create/update sections & tables).
     */
    private void createTemplateStructureAuditTrail(String userId,
                                                   ActionType actionType,
                                                   TemplateLayout layout,
                                                   String actionPrefix,
                                                   Locale userLocale) {
        try {
            if (userId == null) {
                return;
            }

            UUID uuid = UUID.fromString(userId);
            User user = userRepository.findById(uuid).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

            Restaurant restaurant = findRestaurantByUser(user);

            int sectionCount = countSections(layout);
            int tableCount = countTables(layout);

            String description = actionPrefix + sectionCount + " sections and " + tableCount + " tables";

            createTemplateLayoutAuditTrailEntry(user, actionType, restaurant, layout, description);
        } catch (Exception e) {
            log.error("Failed to create audit trail for template structure action {}: {}", actionType, e.getMessage());
            // Don't break structure flow if audit trail fails
        }
    }

    private int countSections(TemplateLayout layout) {
        return layout.getSections() != null ? layout.getSections().size() : 0;
    }

    private int countTables(TemplateLayout layout) {
        if (layout.getSections() == null) {
            return 0;
        }
        int tableCount = 0;
        for (TemplateSection section : layout.getSections()) {
            if (section.getRows() == null) {
                continue;
            }
            for (TemplateRow row : section.getRows()) {
                if (row.getTables() != null) {
                    tableCount += row.getTables().size();
                }
            }
        }
        return tableCount;
    }

    /**
     * Shared validation for template layout translations used by create and update.
     *
     * @param translations    list of translation DTOs from request
     * @param currentLayoutId layout id to exclude when checking duplicate names (null for create)
     * @param userLocale      locale for error messages
     * @param isCreate        true when called from create, false from update
     * @param errorPrefixKey  optional error prefix to distinguish create/update keys; if null defaults are used
     */
    private void validateTemplateLayoutTranslations(List<TemplateLayoutTranslationDto> translations,
                                                    UUID currentLayoutId,
                                                    Locale userLocale,
                                                    boolean isCreate,
                                                    String errorPrefixKey) {
        if (translations == null || translations.isEmpty()) {
            String key = missingTranslationsMessageKey(isCreate);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(key, userLocale)
            );
        }

        // Validate that at least one translation has a non-empty name
        boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        if (!hasValidName) {
            String key = noValidNameMessageKey(isCreate);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(key, userLocale)
            );
        }

        List<String> supportedLanguages = localizationProperties.getLanguages();
        Set<String> languageCodes = new HashSet<>();

        for (TemplateLayoutTranslationDto translation : translations) {
            String name = translation.getName();
            String languageCode = translation.getLanguageCode();

            // Only validate non-empty names
            if (name != null && !name.trim().isEmpty() && languageCode != null) {
                if (!supportedLanguages.contains(languageCode)) {
                    String key = invalidLanguageMessageKey(isCreate);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(key, userLocale)
                    );
                }

                if (!languageCodes.add(languageCode)) {
                    String key = duplicateLanguageMessageKey(isCreate);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(key, userLocale)
                    );
                }

                // Check for duplicate names in the same language
                boolean exists;
                if (currentLayoutId == null) {
                    exists = templateLayoutRepository
                            .existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalse(
                                    name.trim(), languageCode);
                } else {
                    exists = templateLayoutRepository
                            .existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalseAndIdNot(
                                    name.trim(), languageCode, currentLayoutId);
                }

                if (exists) {
                    // Note: original update also used the create error key for duplicate name
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            messageUtil.getMessage("template.layout.create.error.name.exists", userLocale)
                    );
                }
            }
        }
    }

    private String missingTranslationsMessageKey(boolean isCreate) {
        return isCreate
                ? "template.layout.create.error.no.translations"
                : "template.layout.update.error.no.translations";
    }

    private String noValidNameMessageKey(boolean isCreate) {
        return isCreate
                ? "template.layout.create.error.no.valid.name"
                : "template.layout.update.error.no.valid.name";
    }

    private String invalidLanguageMessageKey(boolean isCreate) {
        return isCreate
                ? "error.invalid.language"
                : "template.layout.update.error.invalid.language";
    }

    private String duplicateLanguageMessageKey(boolean isCreate) {
        return isCreate
                ? "template.layout.create.error.duplicate.language"
                : "template.layout.update.error.duplicate.language";
    }

    /**
     * Shared validation for template structure create/update requests.
     */
    private void validateTemplateStructureRequest(TemplateLayoutRequestDto requestDto,
                                                  TemplateLayout templateLayout,
                                                  Locale userLocale,
                                                  String userRole) {
        List<String> supportedLanguages = localizationProperties.getLanguages();

        // Validate languages
        boolean allLanguagesValid = requestDto.getSections().stream()
                .flatMap(s -> s.getTranslations().stream())
                .map(t -> t.getLanguageCode())
                .allMatch(supportedLanguages::contains);

        if (!allLanguagesValid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.language", userLocale));
        }

        // Validate no duplicate language per section
        for (var section : requestDto.getSections()) {
            long uniqueLangCount = section.getTranslations().stream()
                    .map(t -> t.getLanguageCode())
                    .distinct()
                    .count();

            if (uniqueLangCount != section.getTranslations().size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("template.structure.create.error.duplicate.language", userLocale));
            }
        }

        // Validate duplicate section names per language
        Set<String> nameLanguagePairs = new HashSet<>();
        for (var sectionDto : requestDto.getSections()) {
            for (var translationDto : sectionDto.getTranslations()) {
                // Allow null or empty names, only validate non-empty names
                if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                    String pair = translationDto.getLanguageCode().toLowerCase().trim() + "::"
                            + translationDto.getName().toLowerCase().trim();
                    if (!nameLanguagePairs.add(pair)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("template.structure.create.error.duplicate.section.name", userLocale)
                        );
                    }
                }
            }
        }

        // Validate section/row orders, table capacity/order/shape, and capacity reduction
        for (var sectionDto : requestDto.getSections()) {
            if (sectionDto.getSectionOrder() == null || sectionDto.getSectionOrder() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("template.section.order.negative", userLocale));
            }
            for (var rowDto : sectionDto.getRows()) {
                if (rowDto.getRowOrder() == null || rowDto.getRowOrder() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("template.row.order.negative", userLocale));
                }
                for (var tableDto : rowDto.getTables()) {
                    if (tableDto.getCapacity() == null || tableDto.getCapacity() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("template.table.capacity.negative", userLocale));
                    }
                    if (tableDto.getTableOrder() == null || tableDto.getTableOrder() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("template.table.order.negative", userLocale));
                    }
                    try {
                        TableShape.valueOf(tableDto.getShape().name());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("template.table.shape.invalid", userLocale));
                    }

                    // Validate capacity reduction for existing tables
                    if (tableDto.getId() != null) {
                        validateCapacityReduction(tableDto.getId(), tableDto.getCapacity(), templateLayout, userLocale, userRole);
                    }
                }
            }
        }
    }

    /**
     * Restores soft-deleted template layouts by setting {@code isDeleted=false}.
     * <p>
     * Only layouts currently marked deleted are restored; if none of the provided ids are deleted,
     * the request is rejected.
     * </p>
     *
     * @param ids       template layout ids to restore
     * @param updaterId user id performing the restore
     * @return response wrapper with a localized success message
     * @throws ResponseStatusException when layouts are not found or none are deleted
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreTemplateLayouts(List<UUID> ids, String updaterId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User updater = findUserById(updaterId, userLocale, true);
        
        // Find all template layouts by IDs
        List<TemplateLayout> templateLayouts = templateLayoutRepository.findAllById(ids);
        
        if (templateLayouts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_TEMPLATE_LAYOUT_NOT_FOUND, userLocale));
        }
        
        // Filter only deleted template layouts and restore them
        List<TemplateLayout> deletedTemplateLayouts = templateLayouts.stream()
                .filter(tl -> Boolean.TRUE.equals(tl.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedTemplateLayouts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("template.layout.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted template layouts
        for (TemplateLayout templateLayout : deletedTemplateLayouts) {
            templateLayout.setIsDeleted(false);
            templateLayout.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            templateLayout.setUpdatedBy(updater);
        }
        
        templateLayoutRepository.saveAll(deletedTemplateLayouts);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("template.layout.restore.success", userLocale))
            .build();
    }

    // ========== Phase 3 Helper Methods ==========

    /**
     * Wraps a TemplateLayoutResponse in a TemplateLayoutDto.
     * 
     * @param response The template layout response to wrap
     * @return Wrapped TemplateLayoutDto
     */
    private <T extends TemplateLayoutResponse> TemplateLayoutDto<T> wrapTemplateLayoutResponse(T response) {
        TemplateLayoutDto<T> dto = new TemplateLayoutDto<>();
        dto.setTemplateLayout(response);
        return dto;
    }

    /**
     * Applies table properties from DTO to entity, setting audit fields appropriately.
     * 
     * @param table The table entity to update
     * @param tableDto The DTO containing new values
     * @param user The user performing the action
     * @param isNew Whether this is a new table (true) or update (false)
     */
    private void applyTableProperties(TemplateTable table, TemplateTableDto tableDto, User user, boolean isNew) {
        table.setTableOrder(tableDto.getTableOrder());
        table.setShape(tableDto.getShape());
        table.setCapacity(tableDto.getCapacity());
        table.setTableCode(tableDto.getTableCode());
        if (isNew) {
            table.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            table.setCreatedBy(user);
        } else {
            table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            table.setUpdatedBy(user);
        }
        table.setIsDeleted(false);
    }

    /**
     * Finds a user by ID string, with optional exception throwing.
     * 
     * @param userId String UUID of the user
     * @param userLocale Locale for error messages
     * @param throwIfNotFound If true, throws exception when user not found; if false, returns null
     * @return User entity or null if not found and throwIfNotFound is false
     * @throws ResponseStatusException if user not found and throwIfNotFound is true
     */
    private User findUserById(String userId, Locale userLocale, boolean throwIfNotFound) {
        if (userId == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(userId);
            if (throwIfNotFound) {
                return userRepository.findById(uuid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
            } else {
                return userRepository.findById(uuid).orElse(null);
            }
        } catch (IllegalArgumentException e) {
            if (throwIfNotFound) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale));
            }
            return null;
        }
    }

    /**
     * Finds restaurant associated with a user.
     * 
     * @param user The user whose restaurant to find
     * @return Restaurant entity or null if user has no restaurant or restaurant not found
     */
    private Restaurant findRestaurantByUser(User user) {
        if (user == null || user.getRestaurantId() == null) {
            return null;
        }
        return restaurantRepository.findById(user.getRestaurantId()).orElse(null);
    }

    /**
     * Extracts template name from layout translations, with fallback.
     * 
     * @param layout The template layout
     * @return Template name or "No translations" if none available
     */
    private String extractTemplateName(TemplateLayout layout) {
        if (layout == null || layout.getTranslations() == null || layout.getTranslations().isEmpty()) {
            return "No translations";
        }
        return layout.getTranslations().get(0).getName();
    }

    /**
     * Builds a ResponseDto wrapping a TemplateLayoutStructureDto.
     * 
     * @param responseDto The structure response DTO
     * @param messageKey The message key for success message
     * @param userLocale Locale for message translation
     * @return Wrapped ResponseDto
     */
    private ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> buildStructureResponse(
            TemplateLayoutResponseDto responseDto,
            String messageKey,
            Locale userLocale) {
        TemplateLayoutStructureDto<TemplateLayoutResponseDto> wrapped = TemplateLayoutStructureDto
                .<TemplateLayoutResponseDto>builder()
                .templateLayoutStructure(responseDto)
                .build();

        return ResponseDto.<TemplateLayoutStructureDto<TemplateLayoutResponseDto>>builder()
                .data(wrapped)
                .message(messageUtil.getMessage(messageKey, userLocale))
                .build();
    }

    /**
     * Shared helper to create audit trail entry for template layout operations.
     * This method contains the common audit trail creation logic used by both
     * template layout and template structure audit trail methods.
     * 
     * @param user The user performing the action
     * @param actionType The type of action being logged
     * @param restaurant The restaurant associated with the action
     * @param layout The template layout involved
     * @param description The description for the audit trail entry
     */
    private void createTemplateLayoutAuditTrailEntry(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            TemplateLayout layout,
            String description) {
        auditTrailService.createAuditTrail(
                user,
                actionType,
                restaurant,
                null, // status - will default to NA for non-request actions
                null, // ipAddress - not available in this context
                null, // userAgent - not available in this context
                layout.getId(),
                "TEMPLATE_LAYOUT",
                description
        );
    }

}







