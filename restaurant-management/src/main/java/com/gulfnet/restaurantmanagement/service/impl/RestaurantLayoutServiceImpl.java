package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.AppProperties;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.RestaurantLayoutService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.PrintQrCodeService;
import com.gulfnet.restaurantmanagement.service.RestaurantLayoutQrAsyncService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.enums.TableShape;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.model.request.RestaurantRowRequest;
import com.gulfnet.shared_library.model.request.RestaurantSectionRequest;
import com.gulfnet.shared_library.model.request.RestaurantSectionTranslationRequest;
import com.gulfnet.shared_library.model.request.RestaurantTableRequest;
import com.gulfnet.shared_library.model.request.RestaurantLayoutRequestDto;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantLayoutServiceImpl implements RestaurantLayoutService {

    private final RestaurantLayoutRepository restaurantLayoutRepository;
    private final RestaurantSectionTranslationRepository restaurantSectionTranslationRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final TemplateLayoutRepository templateLayoutRepository;
    private final LocalizationProperties localizationProperties;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final AppProperties appProperties;
    private final RestaurantChainConfigProperties configProperties;
    private final SessionRepository sessionRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final OrderRepository orderRepository;
    private final AuditTrailService auditTrailService;
    private final PrintQrCodeService printQrCodeService;
    private final RestaurantLayoutQrAsyncService restaurantLayoutQrAsyncService;

    private static final String msgRestaurantNotFound = "restaurant.not.found";
    private static final String msgRestaurantTableCodeDuplicate = "restaurant.table.code.duplicate";
    
    @PersistenceContext
    private EntityManager entityManager;

    private void runAfterCommit(Runnable runnable) {
        // Ensure async tasks see committed DB state (new tables/rows/sections).
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    /**
     * Creates (or initializes) the restaurant layout structure and persists sections/rows/tables from the request.
     * <p>
     * This method validates:
     * - section translation languages are supported and non-duplicated per section
     * - section names are not duplicated within the same language (ignoring blank/"NA" placeholder values)
     * - table code uniqueness within the layout
     * <p>
     * Side effects:
     * - may create a new {@link RestaurantLayout} if one does not already exist
     * - persists/updates nested entities (sections, rows, tables) and related translations
     * - evicts restaurant-related caches so the updated structure is visible
     *
     * @param restaurantId restaurant id to create structure for (required)
     * @param templateId optional template layout id to link (may be {@code null})
     * @param requestDto requested structure payload (required)
     * @param creatorId actor user id (UUID string) used as createdBy/updatedBy (required)
     * @return wrapper containing the saved restaurant layout structure
     * @throws ResponseStatusException if validation fails or restaurant/template cannot be found
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> createRestaurantStructure(
                UUID restaurantId,
                UUID templateId,                                     // optional, can be null
                RestaurantLayoutRequestDto requestDto,
                String creatorId) {

        Locale userLocale = LocaleContextHolder.getLocale();

        Optional<RestaurantLayout> optionalLayout = restaurantLayoutRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);

        User creator = userRepository.findById(UUID.fromString(creatorId)).orElse(null);

        RestaurantLayout restaurantLayout = optionalLayout.orElseGet(() -> {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));


        RestaurantLayout newLayout = new RestaurantLayout();
        newLayout.setRestaurant(restaurant);
        newLayout.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        newLayout.setCreatedBy(creator);
        newLayout.setIsDeleted(false);
        newLayout.setStatus(EntityStatus.ACTIVE);  // or default status you prefer

        if (templateId != null) {
                TemplateLayout templateLayout = templateLayoutRepository.findById(templateId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("template.layout.not.found", userLocale)));
                newLayout.setTemplateLayout(templateLayout);
        }

        return restaurantLayoutRepository.save(newLayout);
        });

        List<String> supportedLanguages = localizationProperties.getLanguages();

        boolean allLanguagesValid = requestDto.getSections().stream()
                .flatMap(s -> s.getTranslations().stream())
                .map(t -> t.getLanguageCode())
                .allMatch(supportedLanguages::contains);

        if (!allLanguagesValid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.language", userLocale));
        }

        for (var section : requestDto.getSections()) {
            long uniqueLangCount = section.getTranslations().stream()
                    .map(t -> t.getLanguageCode())
                    .distinct()
                    .count();

            if (uniqueLangCount != section.getTranslations().size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("restaurant.structure.create.error.duplicate.language", userLocale));
            }
        }

        Set<String> nameLanguagePairs = new HashSet<>();
        for (var sectionDto : requestDto.getSections()) {
            for (var translationDto : sectionDto.getTranslations()) {
                // Allow null, empty, or "NA" names (treat "NA" as placeholder), only validate non-empty names
                String name = translationDto.getName() != null ? translationDto.getName().trim() : "";
                if (!name.isEmpty() && !name.equalsIgnoreCase("NA")) {
                        String pair = translationDto.getLanguageCode().toLowerCase().trim() + "::" + name.toLowerCase().trim();
                        if (!nameLanguagePairs.add(pair)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.structure.create.error.duplicate.section.name", userLocale));
                        }
                }
            }
        }

        // Collect ALL table IDs from the entire request (for exclusion during validation)
        Set<UUID> allIncomingTableIds = requestDto.getSections().stream()
                .flatMap(section -> section.getRows().stream())
                .flatMap(row -> row.getTables().stream())
                .map(RestaurantTableRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check for duplicate table codes within the request itself
        Map<String, UUID> requestTableCodes = new HashMap<>();
        for (var sectionDto : requestDto.getSections()) {
            for (var rowDto : sectionDto.getRows()) {
                for (var tableDto : rowDto.getTables()) {
                    if (tableDto.getTableCode() != null) {
                        String normalizedCode = tableDto.getTableCode().trim().toLowerCase();
                        UUID existingTableId = requestTableCodes.get(normalizedCode);
                        if (existingTableId != null
                                && (tableDto.getId() == null || !existingTableId.equals(tableDto.getId()))) {
                            // Duplicate code found within the same request
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    messageUtil.getMessage(msgRestaurantTableCodeDuplicate, userLocale));
                        }
                        // Store the table ID for this code (use the ID from request, or generate a temp one for new tables)
                        requestTableCodes.put(normalizedCode, tableDto.getId() != null ? tableDto.getId() : UUID.randomUUID());
                    }
                }
            }
        }

        // Build optimized lookup map ONCE for the entire request (not per table)
        // This avoids rebuilding it multiple times and improves performance significantly
        Map<String, UUID> existingTableCodes = new HashMap<>(); // normalized code -> table ID
        
        if (restaurantLayout.getSections() != null) {
                for (RestaurantSection section : restaurantLayout.getSections()) {
                        if (!Boolean.TRUE.equals(section.getIsDeleted()) && section.getRows() != null) {
                                for (RestaurantRow layoutRow : section.getRows()) {
                                        if (!Boolean.TRUE.equals(layoutRow.getIsDeleted()) && layoutRow.getTables() != null) {
                                                for (RestaurantTable layoutTable : layoutRow.getTables()) {
                                                        if (!Boolean.TRUE.equals(layoutTable.getIsDeleted())
                                                                && layoutTable.getId() != null
                                                                && layoutTable.getTableCode() != null) {
                                                                String normalizedCode = layoutTable.getTableCode().trim().toLowerCase();
                                                                existingTableCodes.put(normalizedCode, layoutTable.getId());
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }

        List<RestaurantSection> sections = new ArrayList<>();

        for (var sectionDto : requestDto.getSections()) {

            if (sectionDto.getSectionOrder() == null || sectionDto.getSectionOrder() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("restaurant.section.order.negative", userLocale));
            }

            for (var translationDto : sectionDto.getTranslations()) {
                // Skip validation for empty or "NA" values (placeholder)
                String name = translationDto.getName() != null ? translationDto.getName().trim() : "";
                if (!name.isEmpty() && !name.equalsIgnoreCase("NA")) {
                        boolean exists = restaurantSectionTranslationRepository.existsByNameLanguageAndLayout(
                                name,
                                translationDto.getLanguageCode(),
                                restaurantLayout.getId());

                        if (exists) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    messageUtil.getMessage("restaurant.structure.create.error.section.name.exists", userLocale));
                        }
                }
            }

            RestaurantSection section = new RestaurantSection();
            section.setRestaurantLayout(restaurantLayout);
            section.setSectionOrder(sectionDto.getSectionOrder());
            section.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            section.setCreatedBy(creator);
            section.setIsDeleted(false);

            section.setTranslations(sectionDto.getTranslations().stream()
                    .filter(tDto -> {
                        String name = tDto.getName() != null ? tDto.getName().trim() : "";
                        return !name.isEmpty() && !name.equalsIgnoreCase("NA");
                    })
                    .map(tDto -> {
                        RestaurantSectionTranslation translation = new RestaurantSectionTranslation();
                        translation.setRestaurantSection(section);
                        translation.setLanguageCode(tDto.getLanguageCode());
                        String name = tDto.getName() != null ? tDto.getName().trim() : "";
                        translation.setName(name);
                        return translation;
                    }).collect(Collectors.toList()));

            List<RestaurantRow> rows = new ArrayList<>();
            for (var rowDto : sectionDto.getRows()) {
                if (rowDto.getRowOrder() == null || rowDto.getRowOrder() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("restaurant.row.order.negative", userLocale));
                }
                RestaurantRow row = new RestaurantRow();
                row.setRestaurantSection(section);
                row.setRowOrder(rowDto.getRowOrder());
                row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setCreatedBy(creator);
                row.setIsDeleted(false);

                List<RestaurantTable> tables = rowDto.getTables().stream()
                        .map(tableDto -> {
                            if (tableDto.getCapacity() != null && tableDto.getCapacity() < 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("restaurant.table.capacity.negative", userLocale));
                            }
                            if (tableDto.getTableOrder() == null || tableDto.getTableOrder() < 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("restaurant.table.order.negative", userLocale));
                            }

                            TableShape shapeEnum;
                            try {
                                shapeEnum = TableShape.valueOf(tableDto.getShape().name());
                            } catch (IllegalArgumentException e) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("restaurant.table.shape.invalid", userLocale));
                            }

                            // Validate table code uniqueness against existing tables in restaurant
                            // Create exclusion set that includes all tables from the request
                            // and this specific table's UUID (if it has one)
                            Set<UUID> exclusionSet = new HashSet<>(allIncomingTableIds);
                            if (tableDto.getId() != null) {
                                exclusionSet.add(tableDto.getId());
                            }
                            validateRestaurantTableCodeUniquenessOptimized(tableDto.getTableCode(), exclusionSet, existingTableCodes, userLocale);

                            RestaurantTable table = new RestaurantTable();
                            table.setRestaurantRow(row);
                            table.setTableOrder(tableDto.getTableOrder());
                            table.setShape(shapeEnum);
                            table.setCapacity(tableDto.getCapacity());
                            table.setTableCode(tableDto.getTableCode());
                            table.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            table.setCreatedBy(creator);
                            table.setIsDeleted(false);
                            table.setTableStatus(TableStatus.BLOCKED);
                            return table;
                        }).collect(Collectors.toList());

                row.setTables(tables);
                rows.add(row);
            }
            section.setRows(rows);
            sections.add(section);
        }

        // Preserve virtual sections before clearing (virtual sections should never be deleted)
        List<RestaurantSection> virtualSections = collectVirtualSections(restaurantLayout.getSections(), "creation");

        restaurantLayout.getSections().clear();
        restaurantLayout.getSections().addAll(sections);
        // Add back preserved virtual sections
        if (!virtualSections.isEmpty()) {
            restaurantLayout.getSections().addAll(virtualSections);
            log.info("Preserved {} virtual section(s) during layout creation", virtualSections.size());
        }

        RestaurantLayout savedLayout = restaurantLayoutRepository.save(restaurantLayout);

        // Generate QR codes asynchronously for tables that don't already have one (exclude virtual tables)
        // This avoids blocking the API response - QR codes will be generated in the background
        if (configProperties.getChain().getQrCodeType() == QrCodeType.STATIC) {
                List<UUID> tablesNeedingQrCodes = new ArrayList<>();
                for (var section : savedLayout.getSections()) {
                        if (!Boolean.TRUE.equals(section.getIsDeleted()) && section.getRows() != null) {
                                for (var row : section.getRows()) {
                                        if (!Boolean.TRUE.equals(row.getIsDeleted()) && row.getTables() != null) {
                                                for (var table : row.getTables()) {
                                                        // Skip virtual tables - they are handled separately during restaurant creation
                                                        if (Boolean.TRUE.equals(table.getIsVirtual())) {
                                                                continue;
                                                        }
                                                        if ((table.getQrCodeUrl() == null || table.getQrCodeUrl().trim().isEmpty())
                                                                && table.getId() != null) {
                                                                tablesNeedingQrCodes.add(table.getId());
                                                        }
                                                }
                                        }
                                }
                        }
                }
                
                // Generate QR codes asynchronously - API response returns immediately
                if (!tablesNeedingQrCodes.isEmpty()) {
                        log.info("Scheduling async QR code generation for {} tables in restaurant {}", 
                                tablesNeedingQrCodes.size(), savedLayout.getRestaurant().getId());
                        runAfterCommit(() -> restaurantLayoutQrAsyncService.generateQrCodesForTablesAsync(
                                savedLayout.getRestaurant().getId(),
                                tablesNeedingQrCodes
                        ));
                }
        }

        RestaurantLayoutStructureDto<RestaurantLayoutResponseDto> wrapped = buildLayoutWrapper(savedLayout);

        // Create audit trail for restaurant structure creation (includes sections and tables)
        try {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            createStructureAuditTrail(
                    creator,
                    ActionType.SECTION_CREATE, // Structure creation includes sections
                    restaurant,
                    restaurantLayout,
                    "created"
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for restaurant structure creation: {}", e.getMessage(), e);
            // Don't break structure creation flow if audit trail fails
        }

        return ResponseDto.<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>>builder()
                .data(wrapped)
                .message(messageUtil.getMessage("restaurant.structure.create.success", userLocale))
                .build();
    }

        private RestaurantLayoutResponseDto mapToResponseDto(RestaurantLayout layout) {
        List<String> supportedLanguages = localizationProperties.getLanguages();
        List<RestaurantSectionResponse> sections = buildSectionResponses(layout, supportedLanguages, false, null);
        return buildRestaurantLayoutResponseDto(layout, sections, LocaleContextHolder.getLocale().getLanguage());
    }

    /**
     * Maps RestaurantLayout to response DTO with fallback logic for GET operations.
     * If exact translation doesn't exist for a language, it will fallback to the next available translation
     * based on the user's preferred language and supported languages order.
     * This ensures records are shown even if exact translation is missing.
     */
    private RestaurantLayoutResponseDto mapToResponseDtoWithFallback(RestaurantLayout layout, String preferredLanguage) {
        List<String> supportedLanguages = localizationProperties.getLanguages();
        List<RestaurantSectionResponse> sections = buildSectionResponses(layout, supportedLanguages, true, preferredLanguage);
        return buildRestaurantLayoutResponseDto(layout, sections, preferredLanguage);
    }

    /**
     * Builds section responses with common filtering and structure logic.
     * The translation building logic differs based on useFallback parameter.
     */
    private List<RestaurantSectionResponse> buildSectionResponses(
            RestaurantLayout layout, 
            List<String> supportedLanguages, 
            boolean useFallback, 
            String preferredLanguage) {
        return layout.getSections().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                // Exclude virtual sections (sections containing virtual tables)
                .filter(s -> {
                    if (s.getRows() == null) {
                        return true;
                    }
                    // Check if any table in this section is virtual
                    boolean hasVirtualTable = s.getRows().stream()
                            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                            .flatMap(r -> r.getTables() != null ? r.getTables().stream() : java.util.stream.Stream.empty())
                            .anyMatch(t -> Boolean.TRUE.equals(t.getIsVirtual()) && !Boolean.TRUE.equals(t.getIsDeleted()));
                    return !hasVirtualTable; // Exclude sections with virtual tables
                })
                .sorted(Comparator.comparing(RestaurantSection::getSectionOrder))
                .map(s -> {
                        List<RestaurantSectionTranslation> sectionTranslations = s.getTranslations();
                        
                        // Create a map of existing translations by language code
                        Map<String, RestaurantSectionTranslation> existingTranslations = sectionTranslations.stream()
                                .collect(Collectors.toMap(
                                        RestaurantSectionTranslation::getLanguageCode,
                                        t -> t,
                                        (existing, replacement) -> existing
                                ));
                        
                        // Build translations list - with or without fallback based on useFallback parameter
                        List<RestaurantSectionTranslationResponse> translations = useFallback
                                ? buildTranslationsWithFallback(supportedLanguages, sectionTranslations, existingTranslations, preferredLanguage)
                                : buildTranslationsWithoutFallback(supportedLanguages, existingTranslations);
                        
                        return RestaurantSectionResponse.builder()
                                .id(s.getId())
                                .sectionOrder(s.getSectionOrder())
                                .translations(translations)
                                .rows(buildRowResponses(s))
                                .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds translations without fallback logic - only exact translations from database.
     */
    private List<RestaurantSectionTranslationResponse> buildTranslationsWithoutFallback(
            List<String> supportedLanguages,
            Map<String, RestaurantSectionTranslation> existingTranslations) {
        return supportedLanguages.stream()
                .map(langCode -> {
                        String name = "";
                        
                        // Only use exact translation if it exists in the database
                        RestaurantSectionTranslation exactTranslation = existingTranslations.get(langCode);
                        if (exactTranslation != null && exactTranslation.getName() != null && !exactTranslation.getName().trim().isEmpty()) {
                                // Exact translation exists - use it
                                name = exactTranslation.getName();
                        }
                        // If no exact translation exists, leave empty (field will be empty for user to fill)
                        
                        return RestaurantSectionTranslationResponse.builder()
                                .languageCode(langCode)
                                .name(name)
                                .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds translations with fallback logic - uses fallback if exact translation doesn't exist.
     */
    private List<RestaurantSectionTranslationResponse> buildTranslationsWithFallback(
            List<String> supportedLanguages,
            List<RestaurantSectionTranslation> sectionTranslations,
            Map<String, RestaurantSectionTranslation> existingTranslations,
            String preferredLanguage) {
        return supportedLanguages.stream()
                .map(langCode -> {
                        String name = "";
                        
                        // First, check if exact translation exists
                        RestaurantSectionTranslation exactTranslation = existingTranslations.get(langCode);
                        if (exactTranslation != null) {
                                // Translation exists in database - use it (even if empty)
                                // Empty translations should remain empty, not be filled with fallback
                                name = exactTranslation.getName() != null ? exactTranslation.getName() : "";
                        } else if (!sectionTranslations.isEmpty()) {
                                // Translation doesn't exist at all - use fallback logic
                                // Use TranslationUtils to find fallback based on preferred language and supported languages order
                                java.util.Optional<RestaurantSectionTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                        sectionTranslations,
                                        preferredLanguage,  // user's preferred language (e.g., "en")
                                        supportedLanguages,  // fallback order from config (e.g., ["en", "ja", "th"])
                                        RestaurantSectionTranslation::getLanguageCode
                                );
                                
                                if (fallback.isPresent() && fallback.get().getName() != null && !fallback.get().getName().trim().isEmpty()) {
                                        // Fallback found with non-empty name - use it so the record is shown even if exact translation is missing
                                        // Example: If English is requested but only Japanese exists, show Japanese
                                        name = fallback.get().getName();
                                }
                                // If no fallback found or fallback is empty, leave empty
                        }
                        // If no translations exist at all, field will be empty
                        
                        return RestaurantSectionTranslationResponse.builder()
                                .languageCode(langCode)
                                .name(name)
                                .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds row responses with tables - common logic for both mapping methods.
     */
    private List<RestaurantRowResponse> buildRowResponses(RestaurantSection section) {
        return section.getRows().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .sorted(Comparator.comparing(RestaurantRow::getRowOrder))
                .map(r -> RestaurantRowResponse.builder()
                        .id(r.getId())
                        .rowOrder(r.getRowOrder())
                        .tables(r.getTables().stream()
                                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                                .filter(t -> !Boolean.TRUE.equals(t.getIsVirtual())) // Exclude virtual tables
                                .sorted(Comparator.comparing(RestaurantTable::getTableOrder))
                                .map(t -> RestaurantTableResponse.builder()
                                        .id(t.getId())
                                        .tableOrder(t.getTableOrder())
                                        .shape(t.getShape())
                                        .capacity(t.getCapacity())
                                        .tableStatus(t.getTableStatus())
                                        .tableCode(t.getTableCode())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Builds the final RestaurantLayoutResponseDto - common logic for both mapping methods.
     */
    private RestaurantLayoutResponseDto buildRestaurantLayoutResponseDto(
            RestaurantLayout layout,
            List<RestaurantSectionResponse> sections,
            String preferredLanguage) {
        UUID templateId = null;
        String templateName = null;
        if (layout.getTemplateLayout() != null) {
                templateId = layout.getTemplateLayout().getId();
                templateName = resolveTemplateName(layout.getTemplateLayout(), preferredLanguage);
        }

        return RestaurantLayoutResponseDto.builder()
                .id(layout.getId())
                .templateId(templateId)
                .templateName(templateName)
                .sections(sections)
                .build();
    }

    private String resolveTemplateName(TemplateLayout templateLayout, String preferredLanguage) {
        if (templateLayout == null || templateLayout.getTranslations() == null || templateLayout.getTranslations().isEmpty()) {
            return null;
        }

        List<String> supportedLanguages = localizationProperties.getLanguages();
        Optional<TemplateLayoutTranslation> preferred = TranslationUtils.pickPreferredOrFromList(
                templateLayout.getTranslations(),
                preferredLanguage,
                supportedLanguages,
                TemplateLayoutTranslation::getLanguageCode
        );

        if (preferred.isPresent() && preferred.get().getName() != null && !preferred.get().getName().trim().isEmpty()) {
            return preferred.get().getName().trim();
        }

        return templateLayout.getTranslations().stream()
                .map(TemplateLayoutTranslation::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * Collect virtual sections from a list, used during layout creation and update.
     */
    private List<RestaurantSection> collectVirtualSections(
            List<RestaurantSection> sections,
            String context) {
        List<RestaurantSection> virtualSections = new ArrayList<>();
        if (sections != null) {
            for (RestaurantSection existingSection : sections) {
                if (Boolean.TRUE.equals(existingSection.getIsDeleted())) {
                    continue;
                }
                boolean isVirtualSection = existingSection.getRows() != null &&
                        existingSection.getRows().stream()
                                .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                                .flatMap(row -> row.getTables() != null ? row.getTables().stream() : java.util.stream.Stream.empty())
                                .anyMatch(table -> Boolean.TRUE.equals(table.getIsVirtual()) && !Boolean.TRUE.equals(table.getIsDeleted()));

                if (isVirtualSection) {
                    virtualSections.add(existingSection);
                    log.debug("Preserving virtual section {} during layout {}", existingSection.getId(), context);
                }
            }
        }
        return virtualSections;
    }


        /**
         * Fetches the persisted restaurant layout structure for display.
         * <p>
         * Uses translation fallback mapping so sections/tables are still visible even when an exact locale translation
         * is missing.
         *
         * @param restaurantId restaurant id to fetch structure for (required)
         * @return wrapper containing the restaurant layout structure
         * @throws ResponseStatusException if the restaurant layout does not exist
         */
        @Override
        @Transactional(readOnly = true)
        public ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> getRestaurantStructure(UUID restaurantId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        RestaurantLayout restaurantLayout = restaurantLayoutRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));


        // Use mapToResponseDtoWithFallback for GET operations to show translations with fallback
        // This ensures records are shown even if exact translation is missing
        String preferredLanguage = userLocale.getLanguage();
        RestaurantLayoutResponseDto responseDto = mapToResponseDtoWithFallback(restaurantLayout, preferredLanguage);

        RestaurantLayoutStructureDto<RestaurantLayoutResponseDto> wrapped = RestaurantLayoutStructureDto.<RestaurantLayoutResponseDto>builder()
                .restaurantLayoutStructure(responseDto)
                .build();

        return ResponseDto.<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>>builder()
                .data(wrapped)
                .message(messageUtil.getMessage("restaurant.structure.get.success", userLocale))
                .build();
        }


        /**
         * Updates an existing restaurant layout structure (sections/rows/tables) from the request payload.
         * <p>
         * Key constraints:
         * - tables that currently have active orders (PUSHED / IN_PROGRESS) cannot be deleted or moved to a different row/section
         * - virtual sections/tables are preserved and protected from deletion
         * <p>
         * Side effects:
         * - upserts nested entities and soft-deletes removed ones
         * - may schedule asynchronous QR generation for tables missing QR codes when chain QR type is STATIC
         * - evicts restaurant-related caches so changes are visible
         * - attempts to write an audit trail entry (non-fatal if audit fails)
         *
         * @param restaurantId restaurant id whose layout is updated (required)
         * @param templateId optional template layout id to link (may be {@code null})
         * @param requestDto updated structure payload (required)
         * @param updaterId actor user id (UUID string) used as updatedBy (required)
         * @return wrapper containing the updated restaurant layout structure
         * @throws ResponseStatusException if validation fails or restaurant/layout/template cannot be found
         */
        @Override
        @Transactional
        @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
        public ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> updateRestaurantStructure(
                UUID restaurantId,
                UUID templateId,   
                RestaurantLayoutRequestDto requestDto,
                String updaterId) {

        Locale userLocale = LocaleContextHolder.getLocale();

        // Fetch layout - collections will be loaded lazily as needed
        // Using regular query to avoid MultipleBagFetchException (Hibernate limitation)
        RestaurantLayout restaurantLayout = restaurantLayoutRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));
        
        // Initialize collections efficiently within transaction to avoid N+1 queries
        // Access collections to trigger batch loading (Hibernate will batch load them)
        if (restaurantLayout.getSections() != null && !restaurantLayout.getSections().isEmpty()) {
                // Trigger loading of sections, rows, and tables in batch
                for (RestaurantSection section : restaurantLayout.getSections()) {
                        if (section.getRows() != null && !section.getRows().isEmpty()) {
                                for (RestaurantRow row : section.getRows()) {
                                        if (row.getTables() != null && !row.getTables().isEmpty()) {
                                                // Access table IDs to ensure they're loaded
                                                row.getTables().forEach(RestaurantTable::getId);
                                        }
                                }
                        }
                }
        }

        // Validation (granular):
        // If a table in the CURRENT layout has active orders (PUSHED / IN_PROGRESS),
        // that table must NOT be deleted and must NOT be moved to a different row/section.
        // Other tables (without active orders) can still be moved/deleted as part of the update.
        record TableLocation(UUID sectionId, UUID rowId) {}

        Map<UUID, TableLocation> currentTableLocations = new HashMap<>();
        Set<UUID> tableIdsInCurrentLayout = new HashSet<>();

        if (restaurantLayout.getSections() != null) {
                for (RestaurantSection section : restaurantLayout.getSections()) {
                        if (section == null || Boolean.TRUE.equals(section.getIsDeleted())) continue;
                        if (section.getRows() == null) continue;
                        for (RestaurantRow row : section.getRows()) {
                                if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) continue;
                                if (row.getTables() == null) continue;
                                for (RestaurantTable table : row.getTables()) {
                                        if (table == null || Boolean.TRUE.equals(table.getIsDeleted())) continue;
                                        if (Boolean.TRUE.equals(table.getIsVirtual())) continue;
                                        if (table.getId() == null) continue;
                                        tableIdsInCurrentLayout.add(table.getId());
                                        currentTableLocations.put(table.getId(), new TableLocation(section.getId(), row.getId()));
                                }
                        }
                }
        }

        if (!tableIdsInCurrentLayout.isEmpty()) {
                List<OrderStatus> blockedOrderStatuses = Arrays.asList(OrderStatus.PUSHED, OrderStatus.IN_PROGRESS);

                // Use existing repository method (by restaurant) and then filter in-memory.
                List<Order> activeOrdersOnRestaurant =
                        orderRepository.findByRestaurantIdAndOrderStatusIn(restaurantId, blockedOrderStatuses);

                Set<UUID> tableIdsWithActiveOrders = new HashSet<>();
                if (activeOrdersOnRestaurant != null) {
                        for (Order o : activeOrdersOnRestaurant) {
                                if (o == null || o.getRestaurantTable() == null || o.getRestaurantTable().getId() == null) continue;
                                UUID tableId = o.getRestaurantTable().getId();
                                if (tableIdsInCurrentLayout.contains(tableId)) {
                                        tableIdsWithActiveOrders.add(tableId);
                                }
                        }
                }

                if (!tableIdsWithActiveOrders.isEmpty()) {
                        Map<UUID, TableLocation> requestedTableLocations = new HashMap<>();
                        if (requestDto.getSections() != null) {
                                for (var sectionDto : requestDto.getSections()) {
                                        if (sectionDto == null || sectionDto.getRows() == null) continue;
                                        UUID reqSectionId = sectionDto.getId();
                                        for (var rowDto : sectionDto.getRows()) {
                                                if (rowDto == null || rowDto.getTables() == null) continue;
                                                UUID reqRowId = rowDto.getId();
                                                for (var tableDto : rowDto.getTables()) {
                                                        if (tableDto == null || tableDto.getId() == null) continue;
                                                        requestedTableLocations.put(tableDto.getId(), new TableLocation(reqSectionId, reqRowId));
                                                }
                                        }
                                }
                        }

                        Set<UUID> lockedTableIdsAttemptedToModify = tableIdsWithActiveOrders.stream().filter(tableId -> {
                                TableLocation currentLoc = currentTableLocations.get(tableId);
                                TableLocation requestedLoc = requestedTableLocations.get(tableId);
                                if (requestedLoc == null) {
                                        // Deleted from layout
                                        return true;
                                }
                                // Moved to another row/section (tableOrder changes within same row are allowed)
                                return currentLoc == null || !Objects.equals(currentLoc.sectionId(), requestedLoc.sectionId())
                                        || !Objects.equals(currentLoc.rowId(), requestedLoc.rowId());
                        }).collect(Collectors.toSet());

                        if (!lockedTableIdsAttemptedToModify.isEmpty()) {
                                Map<UUID, String> tableCodesById = new HashMap<>();
                                if (restaurantLayout.getSections() != null) {
                                        for (RestaurantSection section : restaurantLayout.getSections()) {
                                                if (section == null || Boolean.TRUE.equals(section.getIsDeleted()) || section.getRows() == null) continue;
                                                for (RestaurantRow row : section.getRows()) {
                                                        if (row == null || Boolean.TRUE.equals(row.getIsDeleted()) || row.getTables() == null) continue;
                                                        for (RestaurantTable table : row.getTables()) {
                                                                if (table == null || Boolean.TRUE.equals(table.getIsDeleted()) || table.getId() == null) continue;
                                                                if (table.getTableCode() != null && !table.getTableCode().trim().isEmpty()) {
                                                                        tableCodesById.put(table.getId(), table.getTableCode().trim());
                                                                }
                                                        }
                                                }
                                        }
                                }

                                String lockedCodes = lockedTableIdsAttemptedToModify.stream()
                                        .map(id -> tableCodesById.getOrDefault(id, id.toString()))
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.joining(", "));

                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("restaurant.layout.cannot.modify.tables.with.active.orders", userLocale, lockedCodes));
                        }
                }
        }


        User updater = userRepository.findById(UUID.fromString(updaterId)).orElse(null);

        List<String> supportedLanguages = localizationProperties.getLanguages();

        boolean allLanguagesValid = requestDto.getSections().stream()
                .flatMap(s -> s.getTranslations().stream())
                .map(t -> t.getLanguageCode())
                .allMatch(supportedLanguages::contains);

        if (!allLanguagesValid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("error.invalid.language", userLocale));
        }

        if (templateId != null) {
                TemplateLayout templateLayout = templateLayoutRepository.findById(templateId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("template.layout.not.found", userLocale)));
                restaurantLayout.setTemplateLayout(templateLayout);
        }


        // Validate no duplicate language per section
        for (var section : requestDto.getSections()) {
                long uniqueLangCount = section.getTranslations().stream()
                        .map(RestaurantSectionTranslationRequest::getLanguageCode)
                        .distinct()
                        .count();

                if (uniqueLangCount != section.getTranslations().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("restaurant.structure.create.error.duplicate.language", userLocale));
                }
        }

        Set<String> nameLangPairs = new HashSet<>();
        for (var sectionDto : requestDto.getSections()) {
                for (var translationDto : sectionDto.getTranslations()) {
                        // Allow null or empty names, only validate non-empty names
                        if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty()) {
                                String key = translationDto.getLanguageCode().toLowerCase().trim() + "::" + translationDto.getName().toLowerCase().trim();
                                if (!nameLangPairs.add(key)) {
                                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                messageUtil.getMessage("restaurant.structure.create.error.duplicate.section.name", userLocale));
                                }
                        }
                }
        }

        for (var sectionDto : requestDto.getSections()) {
                if (sectionDto.getSectionOrder() == null || sectionDto.getSectionOrder() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("restaurant.section.order.negative", userLocale));
                }

                for (var rowDto : sectionDto.getRows()) {
                if (rowDto.getRowOrder() == null || rowDto.getRowOrder() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.row.order.negative", userLocale));
                }

                for (var tableDto : rowDto.getTables()) {
                        if (tableDto.getCapacity() == null || tableDto.getCapacity() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.table.capacity.negative", userLocale));
                        }
                        if (tableDto.getTableOrder() == null || tableDto.getTableOrder() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.table.order.negative", userLocale));
                        }
                        try {
                        TableShape.valueOf(tableDto.getShape().name());
                        } catch (IllegalArgumentException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.table.shape.invalid", userLocale));
                        }

                }
                }
        }

        Map<UUID, RestaurantSection> existingSections = restaurantLayout.getSections().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toMap(RestaurantSection::getId, s -> s));

        // Collect ALL table IDs from the entire request (across all sections/rows)
        // This is needed to prevent tables from being marked as deleted when they're moved between sections/rows
        // Also used to exclude all request tables from uniqueness validation
        Set<UUID> allIncomingTableIds = requestDto.getSections().stream()
                .flatMap(section -> section.getRows().stream())
                .flatMap(row -> row.getTables().stream())
                .map(RestaurantTableRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Check for duplicate table codes within the request itself
        Map<String, UUID> requestTableCodes = new HashMap<>();
        for (var sectionDto : requestDto.getSections()) {
            for (var rowDto : sectionDto.getRows()) {
                for (var tableDto : rowDto.getTables()) {
                    if (tableDto.getTableCode() != null) {
                        String normalizedCode = tableDto.getTableCode().trim().toLowerCase();
                        UUID existingTableId = requestTableCodes.get(normalizedCode);
                        if (existingTableId != null
                                && (tableDto.getId() == null || !existingTableId.equals(tableDto.getId()))) {
                            // Duplicate code found within the same request
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    messageUtil.getMessage(msgRestaurantTableCodeDuplicate, userLocale));
                        }
                        // Store the table ID for this code (use the ID from request, or generate a temp one for new tables)
                        requestTableCodes.put(normalizedCode, tableDto.getId() != null ? tableDto.getId() : UUID.randomUUID());
                    }
                }
            }
        }
        
        // Build optimized lookup maps ONCE for the entire request (not per row/section)
        // This avoids rebuilding them multiple times and improves performance significantly
        Map<UUID, RestaurantTable> allLayoutTables = new HashMap<>();
        Map<String, UUID> existingTableCodes = new HashMap<>(); // normalized code -> table ID
        
        if (restaurantLayout.getSections() != null) {
                for (RestaurantSection section : restaurantLayout.getSections()) {
                        if (!Boolean.TRUE.equals(section.getIsDeleted()) && section.getRows() != null) {
                                for (RestaurantRow layoutRow : section.getRows()) {
                                        if (!Boolean.TRUE.equals(layoutRow.getIsDeleted()) && layoutRow.getTables() != null) {
                                                for (RestaurantTable layoutTable : layoutRow.getTables()) {
                                                        if (!Boolean.TRUE.equals(layoutTable.getIsDeleted()) && layoutTable.getId() != null) {
                                                                allLayoutTables.put(layoutTable.getId(), layoutTable);
                                                                // Build table code lookup map for fast validation
                                                                if (layoutTable.getTableCode() != null) {
                                                                        String normalizedCode = layoutTable.getTableCode().trim().toLowerCase();
                                                                        existingTableCodes.put(normalizedCode, layoutTable.getId());
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
        
        // Track all tables that are being moved (tableId -> new row)
        Map<UUID, RestaurantRow> movedTables = new HashMap<>();

        List<RestaurantSection> updatedSections = new ArrayList<>();

        for (var sectionDto : requestDto.getSections()) {
                RestaurantSection section;
                if (sectionDto.getId() != null && existingSections.containsKey(sectionDto.getId())) {
                section = existingSections.get(sectionDto.getId());
                section.setSectionOrder(sectionDto.getSectionOrder());
                section.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                section.setUpdatedBy(updater);
                } else {
                section = new RestaurantSection();
                section.setRestaurantLayout(restaurantLayout);
                section.setSectionOrder(sectionDto.getSectionOrder());
                section.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                section.setCreatedBy(updater);
                section.setIsDeleted(false);
                }

                upsertSectionTranslations(section, sectionDto.getTranslations());
                upsertRows(section, sectionDto.getRows(), updater, allIncomingTableIds, movedTables, restaurantLayout, allIncomingTableIds, allLayoutTables, existingTableCodes);
                updatedSections.add(section);
        }

        Set<UUID> incomingSectionIds = requestDto.getSections().stream()
                .map(RestaurantSectionRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (RestaurantSection existingSection : restaurantLayout.getSections()) {
                // Protect virtual sections from being deleted (sections containing virtual tables)
                boolean isVirtualSection = existingSection.getRows() != null && 
                        existingSection.getRows().stream()
                                .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                                .flatMap(row -> row.getTables() != null ? row.getTables().stream() : java.util.stream.Stream.empty())
                                .anyMatch(table -> Boolean.TRUE.equals(table.getIsVirtual()) && !Boolean.TRUE.equals(table.getIsDeleted()));
                
                if (!incomingSectionIds.contains(existingSection.getId()) 
                        && !Boolean.TRUE.equals(existingSection.getIsDeleted())
                        && !isVirtualSection) { // Protect virtual sections
                existingSection.setIsDeleted(true);
                existingSection.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                existingSection.setUpdatedBy(updater);
                softDeleteRows(existingSection.getRows(), updater);
                }
        }

        List<RestaurantSection> softDeletedSections = restaurantLayout.getSections().stream()
                .filter(RestaurantSection::getIsDeleted)
                .collect(Collectors.toList());

        // Preserve virtual sections before clearing (virtual sections should never be deleted)
        List<RestaurantSection> virtualSections = collectVirtualSections(restaurantLayout.getSections(), "update");

        restaurantLayout.getSections().clear();
        restaurantLayout.getSections().addAll(updatedSections);
        restaurantLayout.getSections().addAll(softDeletedSections);
        // Add back preserved virtual sections
        if (!virtualSections.isEmpty()) {
            restaurantLayout.getSections().addAll(virtualSections);
            log.info("Preserved {} virtual section(s) during layout update", virtualSections.size());
        }

        RestaurantLayout savedLayout = restaurantLayoutRepository.save(restaurantLayout);
        // Flush to ensure all changes including table section moves are persisted immediately
        restaurantLayoutRepository.flush();
        
        log.info("Saved restaurant layout {} for restaurant {}. Flushed to database.", 
                savedLayout.getId(), savedLayout.getRestaurant() != null ? savedLayout.getRestaurant().getId() : "unknown");

        // Generate QR codes asynchronously for tables that don't already have one (exclude virtual tables)
        // This avoids blocking the API response - QR codes will be generated in the background
        if (configProperties.getChain().getQrCodeType() == QrCodeType.STATIC) {
                log.info("QR code type is STATIC, checking for tables needing QR codes for layout {}", savedLayout.getId());
                
                // Query database directly to get all tables for this restaurant layout that need QR codes
                // This ensures we get persisted tables with IDs, avoiding any lazy-loading or persistence issues
                List<RestaurantTable> tablesNeedingQrCodes = restaurantTableRepository.findTablesNeedingQrCodesByLayoutId(savedLayout.getId());
                
                log.info("Query result: Found {} tables needing QR codes for layout {}", 
                        tablesNeedingQrCodes.size(), savedLayout.getId());
                
                if (!tablesNeedingQrCodes.isEmpty()) {
                        List<UUID> tableIds = tablesNeedingQrCodes.stream()
                                .map(RestaurantTable::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        
                        log.info("Found {} tables needing QR code generation for restaurant layout {}. Table IDs: {}, Table Codes: {}", 
                                tableIds.size(), 
                                savedLayout.getId(), 
                                tableIds,
                                tablesNeedingQrCodes.stream()
                                        .map(RestaurantTable::getTableCode)
                                        .collect(Collectors.toList()));
                        
                        // Generate QR codes asynchronously - API response returns immediately
                        log.info("Scheduling async QR code generation for {} tables in restaurant {}", 
                                tableIds.size(), savedLayout.getRestaurant().getId());
                        runAfterCommit(() -> restaurantLayoutQrAsyncService.generateQrCodesForTablesAsync(
                                savedLayout.getRestaurant().getId(),
                                tableIds
                        ));
                } else {
                        log.warn("No tables found needing QR code generation for restaurant layout {}. " +
                                "This may indicate tables already have QR codes, or there's an issue with the query.", 
                                savedLayout.getId());
                }
        } else {
                log.info("QR code type is not STATIC (type: {}), skipping QR code generation", 
                        configProperties.getChain().getQrCodeType());
        }

        RestaurantLayoutStructureDto<RestaurantLayoutResponseDto> wrapped = buildLayoutWrapper(savedLayout);

        // Create audit trail for restaurant structure update (includes sections and tables)
        try {
            Restaurant restaurant = restaurantLayout.getRestaurant();
            createStructureAuditTrail(
                    updater,
                    ActionType.SECTION_UPDATE, // Structure update includes sections
                    restaurant,
                    restaurantLayout,
                    "updated"
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for restaurant structure update: {}", e.getMessage(), e);
            // Don't break structure update flow if audit trail fails
        }

        return ResponseDto.<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>>builder()
                .data(wrapped)
                .message(messageUtil.getMessage("restaurant.structure.update.success", userLocale))
                .build();
        }


        /**
         * Upserts section translations from the request into the given section.
         * <p>
         * Behavior:
         * - non-empty, non-"NA" names update existing translations or create new ones
         * - empty/"NA" names remove the translation for that language if it exists
         * - translations not mentioned in the request are preserved
         *
         * @param section section whose translations are being updated (required)
         * @param translationsDto requested translations (required)
         */
        private void upsertSectionTranslations(RestaurantSection section, List<RestaurantSectionTranslationRequest> translationsDto) {
        if (section.getTranslations() == null) {
                section.setTranslations(new ArrayList<>());
        }

        Map<String, RestaurantSectionTranslation> existingTranslations = section.getTranslations().stream()
                .collect(Collectors.toMap(RestaurantSectionTranslation::getLanguageCode, t -> t));

        // Process translations from the request - update existing or create new ones
        // Existing translations not in the request will be preserved automatically
        for (var tDto : translationsDto) {
                String name = tDto.getName() != null ? tDto.getName().trim() : "";
                
                if (name.isEmpty() || name.equalsIgnoreCase("NA")) {
                        // If empty or "NA", remove the translation if it exists
                        if (existingTranslations.containsKey(tDto.getLanguageCode())) {
                                section.getTranslations().remove(existingTranslations.get(tDto.getLanguageCode()));
                        }
                } else {
                        // Non-empty name - update existing or create new translation
                        RestaurantSectionTranslation translation;
                        if (existingTranslations.containsKey(tDto.getLanguageCode())) {
                                translation = existingTranslations.get(tDto.getLanguageCode());
                                translation.setName(name);
                        } else {
                                translation = new RestaurantSectionTranslation();
                                translation.setRestaurantSection(section);
                                translation.setLanguageCode(tDto.getLanguageCode());
                                translation.setName(name);
                                section.getTranslations().add(translation);
                        }
                }
        }

        // Preserve existing translations that were not in the request
        // (This ensures that updating only English doesn't remove Japanese/Thai translations)
        }

        /**
         * Upserts rows for a section and delegates table upserts per row, while also soft-deleting removed rows.
         * <p>
         * This method is designed to support table moves across rows by ensuring moved tables are added to their new row
         * before the old row collections are processed.
         *
         * @param section section being updated (required)
         * @param rowsDto requested rows for the section (required)
         * @param updater actor performing the update (may be {@code null})
         * @param allIncomingTableIds set of all table ids present in the incoming request (may be empty)
         * @param movedTables accumulator for moved tables (table id -> destination row) (required)
         * @param restaurantLayout parent layout for context/audit (required)
         * @param excludeTableIds table ids to exclude during uniqueness checks (may be {@code null})
         * @param allLayoutTables lookup map of all tables in the layout by id (required)
         * @param existingTableCodes lookup map of normalized table codes to table ids (required)
         */
        private void upsertRows(RestaurantSection section, List<RestaurantRowRequest> rowsDto, User updater, Set<UUID> allIncomingTableIds, Map<UUID, RestaurantRow> movedTables, RestaurantLayout restaurantLayout, Set<UUID> excludeTableIds, Map<UUID, RestaurantTable> allLayoutTables, Map<String, UUID> existingTableCodes) {
        Map<UUID, RestaurantRow> existingRows = section.getRows() == null ? new HashMap<>() :
                section.getRows().stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                        .collect(Collectors.toMap(RestaurantRow::getId, r -> r));

        List<RestaurantRow> updatedRows = new ArrayList<>();

        // First pass: Process all rows and move tables, ensuring moved tables are added to new rows first
        for (var rowDto : rowsDto) {
                RestaurantRow row;
                if (rowDto.getId() != null && existingRows.containsKey(rowDto.getId())) {
                row = existingRows.get(rowDto.getId());
                row.setRowOrder(rowDto.getRowOrder());
                row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setUpdatedBy(updater);
                } else {
                row = new RestaurantRow();
                row.setRestaurantSection(section);
                row.setRowOrder(rowDto.getRowOrder());
                row.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setCreatedBy(updater);
                row.setIsDeleted(false);
                }

                upsertTables(row, rowDto.getTables(), updater, allIncomingTableIds, movedTables, restaurantLayout, excludeTableIds, allLayoutTables, existingTableCodes);

                updatedRows.add(row);
        }
        
        // Second pass: Now process old rows and remove moved tables from their collections
        // This ensures moved tables are already in their new rows before we process old rows

        Set<UUID> incomingRowIds = rowsDto.stream()
                .map(RestaurantRowRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (section.getRows() != null) {
                for (RestaurantRow existingRow : section.getRows()) {
                if (!incomingRowIds.contains(existingRow.getId()) && !Boolean.TRUE.equals(existingRow.getIsDeleted())) {
                        existingRow.setIsDeleted(true);
                        existingRow.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        existingRow.setUpdatedBy(updater);
                        softDeleteTables(existingRow.getTables(), updater);
                }
                }
        }

        if (section.getRows() == null)
                section.setRows(new ArrayList<>());

        List<RestaurantRow> softDeletedRows = section.getRows().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDeleted()))
                .collect(Collectors.toList());

        section.getRows().clear();
        section.getRows().addAll(updatedRows);
        section.getRows().addAll(softDeletedRows);
        }

        /**
         * Upserts tables for a row, including support for moving tables from other rows within the same layout.
         * <p>
         * Also validates table code uniqueness using pre-built lookup maps and enforces constraints such as:
         * - cannot move a soft-deleted table
         * - capacity changes may produce audit trail entries
         *
         * @param row row being updated (required)
         * @param tablesDto requested tables for the row (required)
         * @param updater actor performing the update (may be {@code null})
         * @param allIncomingTableIds set of all table ids present in the incoming request (may be empty)
         * @param movedTables accumulator for moved tables (table id -> destination row) (required)
         * @param restaurantLayout parent layout for context/audit (required)
         * @param excludeTableIds table ids to exclude during uniqueness checks (may be {@code null})
         * @param allLayoutTables lookup map of all tables in the layout by id (required)
         * @param existingTableCodes lookup map of normalized table codes to table ids (required)
         */
        private void upsertTables(RestaurantRow row, List<RestaurantTableRequest> tablesDto, User updater, Set<UUID> allIncomingTableIds, Map<UUID, RestaurantRow> movedTables, RestaurantLayout restaurantLayout, Set<UUID> excludeTableIds, Map<UUID, RestaurantTable> allLayoutTables, Map<String, UUID> existingTableCodes) {
        Locale userLocale = LocaleContextHolder.getLocale();
        Map<UUID, RestaurantTable> existingTables = row.getTables() == null ? new HashMap<>() :
                row.getTables().stream()
                        .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                        .collect(Collectors.toMap(RestaurantTable::getId, t -> t));

        List<RestaurantTable> updatedTables = new ArrayList<>();
        Set<UUID> movedTableIds = new HashSet<>(); // Track tables being moved to this row

        for (var tableDto : tablesDto) {
                // Validate table code uniqueness
                // Create exclusion set that includes:
                // 1. All tables from the current request (excludeTableIds/allIncomingTableIds)
                // 2. This specific table's UUID (if it has one) - to skip self-check
                Set<UUID> exclusionSet = new HashSet<>();
                if (excludeTableIds != null) {
                    exclusionSet.addAll(excludeTableIds);
                }
                // Always exclude this table's own UUID if it exists
                if (tableDto.getId() != null) {
                    exclusionSet.add(tableDto.getId());
                }
                
                validateRestaurantTableCodeUniquenessOptimized(tableDto.getTableCode(), exclusionSet, existingTableCodes, userLocale);
                
                RestaurantTable table;
                if (tableDto.getId() != null && existingTables.containsKey(tableDto.getId())) {
                // Table exists in this row - update it
                table = existingTables.get(tableDto.getId());
                // Track capacity change for audit trail
                Integer oldCapacity = table.getCapacity();
                Integer newCapacity = tableDto.getCapacity();
                boolean capacityChanged = newCapacity != null && !newCapacity.equals(oldCapacity);
                
                table.setTableOrder(tableDto.getTableOrder());
                table.setShape(tableDto.getShape());
                table.setCapacity(tableDto.getCapacity());
                table.setTableCode(tableDto.getTableCode());
                table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                table.setUpdatedBy(updater);
                
                // Create audit trail for table capacity update if capacity changed
                if (capacityChanged && updater != null) {
                    try {
                        Restaurant restaurant = restaurantLayout.getRestaurant();
                        auditTrailService.createAuditTrail(
                                updater,
                                ActionType.TABLE_CAPACITY_UPDATE,
                                restaurant,
                                null, // RequestStatus.NA
                                null, // ipAddress
                                null, // userAgent
                                table.getId(),
                                "TABLE",
                                String.format("Table capacity changed from %d to %d", 
                                        oldCapacity != null ? oldCapacity : 0,
                                        newCapacity != null ? newCapacity : 0)
                        );
                    } catch (Exception e) {
                        log.error("Failed to create audit trail for table capacity update: {}", e.getMessage());
                    }
                }
                } else if (tableDto.getId() != null) {
                // Table ID exists but not in this row - check if it exists anywhere in the restaurant layout
                RestaurantTable existingTable = allLayoutTables.get(tableDto.getId());
                
                if (existingTable != null) {
                        // Table exists in the restaurant layout (in another row/section) - UPDATE and move it
                        // Validate that the table is not soft-deleted
                        if (Boolean.TRUE.equals(existingTable.getIsDeleted())) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("table.cannot.move.deleted", userLocale));
                        }
                        
                        // Validate that the table belongs to the same RestaurantLayout
                        RestaurantSection currentSection = row.getRestaurantSection();
                        RestaurantLayout currentLayout = currentSection != null ? currentSection.getRestaurantLayout() : null;
                        
                        RestaurantRow existingRow = existingTable.getRestaurantRow();
                        RestaurantSection existingSection = existingRow != null ? existingRow.getRestaurantSection() : null;
                        RestaurantLayout existingLayout = existingSection != null ? existingSection.getRestaurantLayout() : null;
                        
                        if (currentLayout == null || existingLayout == null || !currentLayout.getId().equals(existingLayout.getId())) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("table.cannot.move.different.layout", userLocale));
                        }
                        
                        // Table is being moved to this row - UPDATE it
                        UUID oldRowId = existingTable.getRestaurantRow() != null ? existingTable.getRestaurantRow().getId() : null;
                        
                        // Track this move globally (BEFORE updating row reference)
                        movedTables.put(existingTable.getId(), row);
                        
                        // CRITICAL: Update the row reference FIRST, then save
                        // DO NOT remove from old row's collection - let Hibernate handle it automatically
                        // Removing it manually triggers orphanRemoval which tries to DELETE it
                        table = existingTable;
                        // Track capacity change for audit trail
                        Integer oldCapacity = existingTable.getCapacity();
                        Integer newCapacity = tableDto.getCapacity();
                        boolean capacityChanged = newCapacity != null && !newCapacity.equals(oldCapacity);
                        
                        table.setRestaurantRow(row); // Move to new row - this updates the foreign key
                        table.setTableOrder(tableDto.getTableOrder());
                        table.setShape(tableDto.getShape());
                        table.setCapacity(tableDto.getCapacity());
                        table.setTableCode(tableDto.getTableCode());
                        table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        table.setUpdatedBy(updater);
                        movedTableIds.add(table.getId());
                        
                        // Create audit trail for table capacity update if capacity changed
                        if (capacityChanged && updater != null) {
                            try {
                                Restaurant restaurant = restaurantLayout.getRestaurant();
                                auditTrailService.createAuditTrail(
                                        updater,
                                        ActionType.TABLE_CAPACITY_UPDATE,
                                        restaurant,
                                        null, // RequestStatus.NA
                                        null, // ipAddress
                                        null, // userAgent
                                        table.getId(),
                                        "TABLE",
                                        String.format("Table capacity changed from %d to %d", 
                                                oldCapacity != null ? oldCapacity : 0,
                                                newCapacity != null ? newCapacity : 0)
                                );
                            } catch (Exception e) {
                                log.error("Failed to create audit trail for table capacity update: {}", e.getMessage());
                            }
                        }
                        
                        // CRITICAL: Add table to new row's collection BEFORE saving
                        // This ensures Hibernate knows it has a new parent before we process old row
                        if (row.getTables() == null) {
                                row.setTables(new ArrayList<>());
                        }
                        if (!row.getTables().contains(table)) {
                                row.getTables().add(table);
                                log.debug("Added table {} to new row {} collection before saving", table.getId(), row.getId());
                        }
                        
                        // Save the table immediately to persist the row reference change
                        // This updates the restaurant_row_id foreign key in the database
                        restaurantTableRepository.save(table);
                        // Don't flush yet - wait until after we've processed all rows
                        // Flushing now and refreshing would clear the old row's collection before we can process it
                        log.debug("Moving table {} from row {} to row {} (section change) - saved (will flush later)", 
                                table.getId(), oldRowId, row.getId());
                } else {
                        // Table ID provided but NOT found in restaurant layout - CREATE new table (ignore the provided ID)
                        // This happens when the ID doesn't exist in the restaurant layout
                        log.info("Table ID {} not found in restaurant layout, creating new table with code {}", 
                                tableDto.getId(), tableDto.getTableCode());
                        table = new RestaurantTable();
                        table.setRestaurantRow(row);
                        table.setTableOrder(tableDto.getTableOrder());
                        table.setShape(tableDto.getShape());
                        table.setCapacity(tableDto.getCapacity());
                        table.setTableCode(tableDto.getTableCode());
                        table.setTableStatus(TableStatus.BLOCKED);
                        table.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        table.setCreatedBy(updater);
                        table.setIsDeleted(false);
                }
                } else {
                // New table - create it
                table = new RestaurantTable();
                table.setRestaurantRow(row);
                table.setTableOrder(tableDto.getTableOrder());
                table.setShape(tableDto.getShape());
                table.setCapacity(tableDto.getCapacity());
                table.setTableCode(tableDto.getTableCode());
                table.setTableStatus(TableStatus.BLOCKED);
                table.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                table.setCreatedBy(updater);
                table.setIsDeleted(false);
                }

                // CRITICAL: Always add table to updatedTables - this ensures it's included in finalTables
                updatedTables.add(table);
                log.debug("Added table {} (code: {}, order: {}) to updatedTables for row {}", 
                        table.getId() != null ? table.getId() : "new", 
                        table.getTableCode(), 
                        table.getTableOrder(),
                        row.getId() != null ? row.getId() : "new");
        }

        Set<UUID> incomingTableIds = tablesDto.stream()
                .map(RestaurantTableRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.debug("Processing {} tables for row {}: incomingTableIds={}, updatedTables.size()={}", 
                tablesDto.size(), row.getId() != null ? row.getId() : "new", incomingTableIds, updatedTables.size());

        // Build new list without using clear() to avoid orphanRemoval issues
        // DO NOT manually remove moved tables from collections - this triggers orphanRemoval
        // Instead, just skip them when building finalTables - Hibernate will handle collection updates
        List<RestaurantTable> finalTables = new ArrayList<>();
        
        // Create a snapshot of the current collection to avoid concurrent modification
        List<RestaurantTable> currentTables = row.getTables() != null ? new ArrayList<>(row.getTables()) : new ArrayList<>();
        
        log.debug("Row {} has {} existing tables in currentTables", row.getId() != null ? row.getId() : "new", currentTables.size());
        
        // Add tables that are staying in this row (updated or unchanged)
        for (RestaurantTable existingTable : currentTables) {
                UUID tableId = existingTable.getId();
                
                // CRITICAL: Check if this table was moved to a different row
                // If the table's row reference has changed, skip it (it's been moved)
                if (movedTables.containsKey(tableId)) {
                        RestaurantRow targetRow = movedTables.get(tableId);
                        // If this table is being moved to a different row, skip it
                        if (targetRow != null && !targetRow.getId().equals(row.getId())) {
                                log.debug("Skipping table {} in row {} - it's being moved to row {}", 
                                        tableId, row.getId(), targetRow.getId());
                                continue;
                        }
                }
                
                // Also check if the table's row reference has already been changed (defensive check)
                if (existingTable.getRestaurantRow() != null && !existingTable.getRestaurantRow().getId().equals(row.getId())) {
                        log.debug("Skipping table {} - its row reference has changed from {} to {}", 
                                tableId, row.getId(), existingTable.getRestaurantRow().getId());
                        continue;
                }
                
                // Skip if table is being moved to another row (already removed from old row above)
                if (movedTableIds.contains(tableId)) {
                        continue;
                }
                
                // Skip if table is in updatedTables (will be added separately)
                if (incomingTableIds.contains(tableId)) {
                        continue;
                }
                
                // Only mark as deleted if:
                // 1. It's not in the incoming tables for this row, AND
                // 2. It's not in the global set of all incoming table IDs (meaning it wasn't moved to another row)
                // 3. It's not a virtual table (virtual tables should never be deleted via layout APIs)
                if (!incomingTableIds.contains(tableId) 
                        && !allIncomingTableIds.contains(tableId)
                        && !Boolean.TRUE.equals(existingTable.getIsDeleted())
                        && !Boolean.TRUE.equals(existingTable.getIsVirtual())) { // Protect virtual tables
                        // Table is truly being deleted (not moved)
                        // Validate that table has no ongoing orders
                        if (hasOngoingOrders(existingTable)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("table.cannot.delete.ongoing.orders", userLocale));
                        }
                        existingTable.setIsDeleted(true);
                        existingTable.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        existingTable.setUpdatedBy(updater);
                        // Clean up sessions and assignments for deleted table
                        cleanupTableSessionsAndAssignments(existingTable, updater);
                        log.debug("Soft-deleting table {} as it's not in the request", existingTable.getId());
                }
                
                // Add to final list (including soft-deleted ones)
                finalTables.add(existingTable);
        }
        
        // Add all updated/new/moved tables
        finalTables.addAll(updatedTables);
        
        log.debug("After adding updatedTables, finalTables.size()={} (updatedTables.size()={})", 
                finalTables.size(), updatedTables.size());
        
        // CRITICAL: Don't use clear() - it triggers orphanRemoval for ALL tables
        // Instead, manually manage the collection by removing only what needs to be removed
        if (row.getTables() == null) {
                row.setTables(new ArrayList<>());
        }
        
        // Get final table IDs (filter out null IDs for new tables)
        Set<UUID> finalTableIds = finalTables.stream()
                .map(RestaurantTable::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Also track new tables (without IDs) by their table codes for logging
        List<String> newTableCodes = finalTables.stream()
                .filter(t -> t.getId() == null)
                .map(RestaurantTable::getTableCode)
                .collect(Collectors.toList());
        log.debug("finalTables contains {} tables with IDs and {} new tables (codes: {})", 
                finalTableIds.size(), newTableCodes.size(), newTableCodes);
        
        // Remove tables that are NOT in the final list (but only if they're not moved)
        // CRITICAL: Do NOT remove moved tables - they've already been updated with new row reference
        // Removing them would trigger orphanRemoval and cause Hibernate to try to DELETE them
        List<RestaurantTable> toRemove = new ArrayList<>();
        // Create a copy to iterate over to avoid concurrent modification
        List<RestaurantTable> tablesToCheck = new ArrayList<>(row.getTables());
        for (RestaurantTable currentTable : tablesToCheck) {
                UUID tableId = currentTable.getId();
                
                // Skip tables with null IDs (shouldn't happen for existing tables, but defensive check)
                if (tableId == null) {
                        log.warn("Found table with null ID in row {} - skipping removal check", row.getId());
                        continue;
                }
                
                // CRITICAL CHECKS - Do NOT remove if any of these are true:
                // 1. Table is in movedTables map (being moved to another row)
                boolean isMoved = movedTables.containsKey(tableId);
                
                // 2. Table's row reference has changed (it's been moved)
                boolean rowReferenceChanged = currentTable.getRestaurantRow() != null && 
                        !currentTable.getRestaurantRow().getId().equals(row.getId());
                
                // 3. Table is in allIncomingTableIds (it exists somewhere in the request, just not this row)
                boolean existsInRequest = allIncomingTableIds.contains(tableId);
                
                // Only remove if:
                // - It's not in the final list for this row, AND
                // - It's not being moved, AND
                // - Its row reference hasn't changed, AND
                // - It's not in the global request (meaning it's truly being deleted)
                // - It's not a virtual table (virtual tables should never be deleted via layout APIs)
                if (!finalTableIds.contains(tableId) && !isMoved && !rowReferenceChanged && !existsInRequest
                        && !Boolean.TRUE.equals(currentTable.getIsVirtual())) { // Protect virtual tables
                        toRemove.add(currentTable);
                        log.debug("Marking table {} for removal from row {} - it's being deleted", tableId, row.getId());
                } else {
                        // Table is being moved or exists elsewhere - DO NOT remove it
                        if (isMoved) {
                                log.debug("Skipping removal of table {} - it's in movedTables map", tableId);
                        } else if (rowReferenceChanged) {
                                log.debug("Skipping removal of table {} - its row reference has changed to {}", 
                                        tableId, currentTable.getRestaurantRow() != null ? currentTable.getRestaurantRow().getId() : "null");
                        } else if (existsInRequest) {
                                log.debug("Skipping removal of table {} - it exists in the request (being moved)", tableId);
                        }
                }
        }
        // Only remove tables that are truly being deleted, not moved
        if (!toRemove.isEmpty()) {
                log.debug("Removing {} tables from row {} that are being deleted", toRemove.size(), row.getId());
                row.getTables().removeAll(toRemove);
        } else {
                log.debug("No tables to remove from row {} - all tables are either staying or being moved", row.getId());
        }
        
        // Add tables that are NOT already in the collection
        // CRITICAL: Ensure all tables in finalTables are in the row's collection
        // Since the relationship is mappedBy, setting table.setRestaurantRow(row) doesn't automatically add to collection
        // We must manually add all tables to ensure they're persisted
        for (RestaurantTable finalTable : finalTables) {
                UUID tableId = finalTable.getId();
                
                // Ensure the table's row reference is set (required for persistence)
                if (finalTable.getRestaurantRow() == null || 
                    finalTable.getRestaurantRow().getId() == null || 
                    !finalTable.getRestaurantRow().getId().equals(row.getId())) {
                        finalTable.setRestaurantRow(row);
                }
                
                // Check if table is already in collection (by ID for existing tables, by table code for new tables)
                boolean alreadyInCollection = false;
                if (tableId != null) {
                        // Existing table - match by ID (null-safe comparison)
                        alreadyInCollection = row.getTables().stream()
                                .anyMatch(t -> t.getId() != null && tableId.equals(t.getId()));
                } else {
                        // New table without ID - match by table code (since IDs aren't set yet)
                        // This is more reliable than object reference comparison
                        String tableCode = finalTable.getTableCode();
                        if (tableCode != null) {
                                alreadyInCollection = row.getTables().stream()
                                        .anyMatch(t -> t.getId() == null && tableCode.equals(t.getTableCode()));
                        } else {
                                // Fallback to object reference if no table code
                                alreadyInCollection = row.getTables().contains(finalTable);
                        }
                }
                
                if (!alreadyInCollection) {
                        row.getTables().add(finalTable);
                        log.debug("Added table {} (code: {}, order: {}) to row {} collection from finalTables", 
                                tableId != null ? tableId : "new", 
                                finalTable.getTableCode(),
                                finalTable.getTableOrder(),
                                row.getId() != null ? row.getId() : "new");
                } else {
                        // Table is already in collection - ensure it's the same instance
                        // Replace it with the updated instance from finalTables
                        if (tableId != null) {
                                // Existing table - remove by ID (null-safe comparison)
                                row.getTables().removeIf(t -> t.getId() != null && tableId.equals(t.getId()));
                        } else {
                                // New table - remove by table code match
                                String tableCode = finalTable.getTableCode();
                                if (tableCode != null) {
                                        row.getTables().removeIf(t -> t.getId() == null && tableCode.equals(t.getTableCode()));
                                } else {
                                        row.getTables().remove(finalTable);
                                }
                        }
                        row.getTables().add(finalTable);
                        log.debug("Replaced table {} (code: {}, order: {}) in row {} collection with updated instance", 
                                tableId != null ? tableId : "new",
                                finalTable.getTableCode(),
                                finalTable.getTableOrder(),
                                row.getId() != null ? row.getId() : "new");
                }
        }
        
        log.debug("Final row {} collection has {} tables after processing finalTables: {}", 
                row.getId() != null ? row.getId() : "new", 
                row.getTables().size(),
                row.getTables().stream()
                        .map(t -> t.getTableCode() + " (order: " + t.getTableOrder() + ")")
                        .collect(Collectors.joining(", ")));
        }

        private void softDeleteRows(List<RestaurantRow> rows, User updater) {
        for (RestaurantRow row : rows) {
                if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                row.setIsDeleted(true);
                row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                row.setUpdatedBy(updater);
                softDeleteTables(row.getTables(), updater);
                }
        }
        }

        /**
         * Soft-deletes tables by setting {@code isDeleted=true} and cleaning up related resources.
         * <p>
         * Constraints:
         * - a table with ongoing orders (PUSHED / IN_PROGRESS) cannot be deleted
         * <p>
         * Side effects:
         * - deletes existing QR image from S3 (best-effort)
         * - expires active sessions and unassigns waiters for the deleted table (best-effort)
         *
         * @param tables tables to soft-delete (required)
         * @param updater actor performing the deletion (may be {@code null})
         * @throws ResponseStatusException if any table has ongoing orders
         */
        private void softDeleteTables(List<RestaurantTable> tables, User updater) {
        for (RestaurantTable table : tables) {
                if (!Boolean.TRUE.equals(table.getIsDeleted())) {
                        // Validate that table has no ongoing orders
                        if (hasOngoingOrders(table)) {
                                Locale userLocale = LocaleContextHolder.getLocale();
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("table.cannot.delete.ongoing.orders", userLocale));
                        }
                        table.setIsDeleted(true);
                        table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        table.setUpdatedBy(updater);

                        if (table.getQrCodeUrl() != null && !table.getQrCodeUrl().isEmpty()) {
                                try {
                                        awsService.deleteFile(table.getQrCodeUrl());
                                } catch (Exception e) {
                                        log.error("Failed to delete QR code from S3 for table {}: {}", table.getId(), e.getMessage());
                                }
                        }
                
                        // Clean up sessions and assignments for deleted table
                        cleanupTableSessionsAndAssignments(table, updater);
                }
        }
        }
        
        /**
         * Check if a table has any ongoing orders (PUSHED or IN_PROGRESS status).
         * 
         * @param table The table to check
         * @return true if the table has ongoing orders, false otherwise
         */
        private boolean hasOngoingOrders(RestaurantTable table) {
                // Get all active sessions for this table
                List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(table.getId());
                
                if (activeSessions.isEmpty()) {
                        return false;
                }
                
                // Get all session IDs
                List<UUID> sessionIds = activeSessions.stream()
                        .map(Session::getId)
                        .collect(Collectors.toList());
                
                // Get all orders for these sessions
                List<Order> orders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
                
                // Check if any order has status PUSHED or IN_PROGRESS
                return orders.stream()
                        .anyMatch(order -> order.getOrderStatus() == OrderStatus.PUSHED 
                                || order.getOrderStatus() == OrderStatus.IN_PROGRESS);
        }
        
        /**
         * Clean up active sessions and waiter assignments for a soft-deleted table.
         * This ensures that deleted tables cannot be used for ordering or assigned to waiters.
         * 
         * @param table The table that is being soft-deleted
         * @param updater The user performing the deletion
         */
        private void cleanupTableSessionsAndAssignments(RestaurantTable table, User updater) {
                try {
                        // Expire all active sessions for this table
                        List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(table.getId());
                        if (!activeSessions.isEmpty()) {
                                OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC);
                                for (Session session : activeSessions) {
                                        session.setExpiredAt(expiredAt);
                                }
                                sessionRepository.saveAll(activeSessions);
                                log.info("Expired {} active session(s) for deleted table {}", activeSessions.size(), table.getId());
                        }
                        
                        // Unassign all waiters from this table (handle multiple assignments)
                        List<TableAssignment> activeAssignments = tableAssignmentRepository
                                .findByRestaurantTableIdAndUnassignedAtIsNull(table.getId());
                        int unassignedCount = 0;
                        for (TableAssignment assignment : activeAssignments) {
                                assignment.setUnassignedAt(OffsetDateTime.now(ZoneOffset.UTC));
                                assignment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                                assignment.setUpdatedBy(updater);
                                tableAssignmentRepository.save(assignment);
                                unassignedCount++;
                                log.debug("Unassigned waiter {} from deleted table {}", assignment.getWaiter().getId(), table.getId());
                        }
                        if (unassignedCount > 0) {
                                log.info("Unassigned {} waiter assignment(s) from deleted table {}", unassignedCount, table.getId());
                        }
                } catch (Exception e) {
                        // Log error but don't fail the deletion process
                        log.error("Failed to cleanup sessions and assignments for deleted table {}: {}", table.getId(), e.getMessage(), e);
                }
        }

        /**
         * Generates a QR image for a (non-virtual) table, uploads it to S3, optionally generates a PDF, and persists URLs.
         * <p>
         * The QR content is built as:
         * {@code <baseUrl>/customer/r/<restaurantId>/<tableId>}
         *
         * @param restaurantId restaurant id (required)
         * @param table table to generate QR for (required; must have id)
         * @throws RuntimeException if QR generation/upload fails
         */
        void generateAndUploadQr(UUID restaurantId, RestaurantTable table) {

                UUID tableId = table.getId();
                String qrContent = String.format("%s/customer/r/%s/%s", appProperties.getBaseUrl(), restaurantId, tableId);
                try {
                        QRCodeWriter qrCodeWriter = new QRCodeWriter();
                        var bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 250, 250);
                        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(qrImage, "png", baos);
                        byte[] bytes = baos.toByteArray();
                        InputStream inputStream = new ByteArrayInputStream(bytes);

                        String fileName = "table-id_" + tableId.toString() + ".png";
                        String s3Key = "qr-codes/" + restaurantId.toString() + "/" + fileName;

                        String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, bytes.length);

                        table.setQrCodeUrl(uploadedFileUrl); 

                        // Generate and upload QR code PDF
                        try {
                                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                                if (restaurant != null) {
                                        String pdfUrl = printQrCodeService.generateQrCodePdf(restaurant, table);
                                        table.setPrintQrCodeUrl(pdfUrl);
                                        log.info("QR code PDF generated and uploaded for table {}: {}", tableId, pdfUrl);
                                } else {
                                        log.warn("Restaurant not found for ID {} when generating QR code PDF for table {}", restaurantId, tableId);
                                }
                        } catch (Exception e) {
                                log.error("Failed to generate QR code PDF for table {}: {}", tableId, e.getMessage(), e);
                                // Don't fail the QR code generation if PDF generation fails
                        }

                        // Save table with QR code URLs to database (SYNCHRONOUS)
                        restaurantTableRepository.save(table);
                        log.info("QR code URLs saved to database for table {}: qrCodeUrl={}, printQrCodeUrl={}", 
                                tableId, table.getQrCodeUrl(), table.getPrintQrCodeUrl());

                } catch (WriterException | java.io.IOException e) {
                        throw new RuntimeException("Failed to generate/upload QR code: " + e.getMessage());
                }
        }

        /**
         * Generates a QR image for a virtual table, uploads it to S3, optionally generates a PDF, and persists URLs.
         * <p>
         * Virtual table QR content includes {@code ?isVirtual=true} so the customer flow can differentiate.
         *
         * @param restaurantId restaurant id (required)
         * @param table virtual table to generate QR for (required; must have id)
         * @throws RuntimeException if QR generation/upload fails
         */
        void generateAndUploadQrForVirtualTable(UUID restaurantId, RestaurantTable table) {

                UUID tableId = table.getId();
                String qrContent = String.format("%s/customer/r/%s/%s?isVirtual=true", appProperties.getBaseUrl(), restaurantId, tableId);
                try {
                        QRCodeWriter qrCodeWriter = new QRCodeWriter();
                        var bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 250, 250);
                        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(qrImage, "png", baos);
                        byte[] bytes = baos.toByteArray();
                        InputStream inputStream = new ByteArrayInputStream(bytes);

                        String fileName = "table-id_" + tableId.toString() + ".png";
                        String s3Key = "qr-codes/" + restaurantId.toString() + "/" + fileName;

                        String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, bytes.length);

                        table.setQrCodeUrl(uploadedFileUrl); 

                        // Generate and upload QR code PDF
                        try {
                                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                                if (restaurant != null) {
                                        String pdfUrl = printQrCodeService.generateQrCodePdf(restaurant, table);
                                        table.setPrintQrCodeUrl(pdfUrl);
                                        log.info("QR code PDF generated and uploaded for virtual table {}: {}", tableId, pdfUrl);
                                } else {
                                        log.warn("Restaurant not found for ID {} when generating QR code PDF for virtual table {}", restaurantId, tableId);
                                }
                        } catch (Exception e) {
                                log.error("Failed to generate QR code PDF for virtual table {}: {}", tableId, e.getMessage(), e);
                                // Don't fail the QR code generation if PDF generation fails
                        }

                        // Save table with QR code URLs to database (SYNCHRONOUS)
                        restaurantTableRepository.save(table);
                        log.info("QR code URLs saved to database for virtual table {}: qrCodeUrl={}, printQrCodeUrl={}", 
                                tableId, table.getQrCodeUrl(), table.getPrintQrCodeUrl());

                } catch (WriterException | java.io.IOException e) {
                        throw new RuntimeException("Failed to generate/upload QR code for virtual table: " + e.getMessage());
                }
        }

    /**
     * Optimized validation that uses pre-built lookup maps instead of iterating through the entire layout.
     * This is O(1) lookup instead of O(n) iteration, significantly improving performance.
     * 
     * @param tableCode The table code to validate
     * @param excludeTableIds Set of table IDs to exclude from the uniqueness check (for updates - all tables in the current request)
     * @param existingTableCodes Pre-built map of normalized table codes to table IDs
     * @param userLocale The user's locale for error messages
     * @throws ResponseStatusException if the table code already exists
     */
    private void validateRestaurantTableCodeUniquenessOptimized(String tableCode, Set<UUID> excludeTableIds, Map<String, UUID> existingTableCodes, Locale userLocale) {
        if (tableCode == null || tableCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.table.code.required", userLocale));
        }

        String normalizedTableCode = tableCode.trim().toLowerCase();
        log.debug("Validating table code '{}' (normalized: '{}'), excluding {} table IDs", 
                tableCode, normalizedTableCode, excludeTableIds != null ? excludeTableIds.size() : 0);

        // Fast O(1) lookup instead of O(n) iteration
        UUID existingTableId = existingTableCodes.get(normalizedTableCode);
        if (existingTableId != null
                && (excludeTableIds == null || !excludeTableIds.contains(existingTableId))) {
            log.error("Table code conflict detected! Code '{}' already exists for table ID: {}. " +
                    "Excluding table IDs: {}",
                    tableCode, existingTableId, excludeTableIds);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage(msgRestaurantTableCodeDuplicate, userLocale));
        }
    }

    /**
     * Validates that the table code is unique within the restaurant layout.
     * Case-insensitive comparison is performed.
     * NOTE: This method is kept for backward compatibility but should use the optimized version when possible.
     * 
     * @param restaurantLayout The restaurant layout to check within
     * @param tableCode The table code to validate
     * @param excludeTableIds Set of table IDs to exclude from the uniqueness check (for updates - all tables in the current request)
     * @param userLocale The user's locale for error messages
     * @throws ResponseStatusException if the table code already exists
     */
    private void validateRestaurantTableCodeUniqueness(RestaurantLayout restaurantLayout, String tableCode, Set<UUID> excludeTableIds, Locale userLocale) {
        if (tableCode == null || tableCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.table.code.required", userLocale));
        }

        String normalizedTableCode = tableCode.trim().toLowerCase();
        log.debug("Validating table code '{}' (normalized: '{}'), excluding {} table IDs", 
                tableCode, normalizedTableCode, excludeTableIds != null ? excludeTableIds.size() : 0);

        // Search through all tables in the restaurant layout
        for (RestaurantSection section : restaurantLayout.getSections()) {
            if (!Boolean.TRUE.equals(section.getIsDeleted())) {
                for (RestaurantRow row : section.getRows()) {
                    if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                        for (RestaurantTable table : row.getTables()) {
                            // Skip soft-deleted tables
                            if (Boolean.TRUE.equals(table.getIsDeleted())) {
                                continue;
                            }
                            
                            // Skip tables from the current request (if excludeTableIds is provided)
                            if (excludeTableIds != null && excludeTableIds.contains(table.getId())) {
                                log.debug("Skipping table {} (ID: {}) - it's in the exclude list", 
                                        table.getTableCode(), table.getId());
                                continue;
                            }
                            
                            // Case-insensitive comparison
                            if (table.getTableCode() != null && 
                                table.getTableCode().trim().toLowerCase().equals(normalizedTableCode)) {
                                log.error("Table code conflict detected! Code '{}' already exists for table ID: {} (section: {}, row: {}). " +
                                        "Excluding table IDs: {}", 
                                        tableCode, table.getId(), 
                                        section.getId(), row.getId(), excludeTableIds);
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        messageUtil.getMessage(msgRestaurantTableCodeDuplicate, userLocale));
                            }
                        }
                    }
                }
            }
        }
    }

    private RestaurantLayoutStructureDto<RestaurantLayoutResponseDto> buildLayoutWrapper(RestaurantLayout savedLayout) {
        RestaurantLayoutResponseDto responseDto = mapToResponseDto(savedLayout);
        return RestaurantLayoutStructureDto.<RestaurantLayoutResponseDto>builder()
                .restaurantLayoutStructure(responseDto)
                .build();
    }

    /**
     * Creates an audit trail entry describing a restaurant layout structure change.
     * <p>
     * The message includes a summary of section and table counts at the time of the action.
     *
     * @param actor user who performed the action (may be {@code null})
     * @param actionType audit action type (required)
     * @param restaurant restaurant context (required)
     * @param restaurantLayout layout that was created/updated (required)
     * @param actionVerb verb used in the audit message (e.g. "created", "updated") (required)
     */
    private void createStructureAuditTrail(
            User actor,
            ActionType actionType,
            Restaurant restaurant,
            RestaurantLayout restaurantLayout,
            String actionVerb) {

        int sectionCount = restaurantLayout.getSections() != null ? restaurantLayout.getSections().size() : 0;
        int tableCount = restaurantLayout.getSections() != null ?
                restaurantLayout.getSections().stream()
                        .mapToInt(s -> s.getRows() != null ?
                                s.getRows().stream()
                                        .mapToInt(r -> r.getTables() != null ? r.getTables().size() : 0)
                                        .sum() : 0)
                        .sum() : 0;

        auditTrailService.createAuditTrail(
                actor,
                actionType,
                restaurant,
                null, // status - will default to NA for non-request actions
                null, // ipAddress - not available in this context
                null, // userAgent - not available in this context
                restaurantLayout.getId(),
                "RESTAURANT_LAYOUT",
                "Restaurant structure " + actionVerb + " with " + sectionCount + " sections and " + tableCount + " tables"
        );
    }
}




