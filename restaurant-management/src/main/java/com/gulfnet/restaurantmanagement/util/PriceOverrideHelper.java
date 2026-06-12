package com.gulfnet.restaurantmanagement.util;

import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.repository.PriceOverrideMappingRepository;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for handling price override logic with DATE + TIME combination
 * This class is shared between MenuServiceImpl and ItemServiceImpl to avoid code duplication
 */
@Slf4j
@Component
public class PriceOverrideHelper {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final PriceOverrideMappingRepository priceOverrideMappingRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;

    public PriceOverrideHelper(PriceOverrideMappingRepository priceOverrideMappingRepository,
            MenuCategoryMappingRepository menuCategoryMappingRepository) {
        this.priceOverrideMappingRepository = priceOverrideMappingRepository;
        this.menuCategoryMappingRepository = menuCategoryMappingRepository;
    }

    /**
     * Index structure to cache active price overrides by category and menu
     */
    public static final class ActiveOverrideIndex {
        private final Map<UUID, PriceOverride> categoryOverrides = new HashMap<>();
        private final Map<UUID, PriceOverride> menuOverrides = new HashMap<>();

        public Map<UUID, PriceOverride> getCategoryOverrides() {
            return categoryOverrides;
        }

        public Map<UUID, PriceOverride> getMenuOverrides() {
            return menuOverrides;
        }
    }

    /**
     * Build an index of active price overrides for a restaurant
     * Uses the new DATE + TIME combination logic via isOverrideActive()
     */
    public ActiveOverrideIndex buildActiveOverrideIndex(UUID restaurantId) {
        ActiveOverrideIndex index = new ActiveOverrideIndex();

        log.debug("Building active override index for restaurant: {}", restaurantId);

        List<PriceOverride> overrides = priceOverrideMappingRepository.findDistinctPriceOverridesByRestaurantId(restaurantId);
        if (overrides == null || overrides.isEmpty()) {
            log.debug("No price overrides found for restaurant: {}", restaurantId);
            return index;
        }

        log.debug("Found {} total price overrides for restaurant", overrides.size());
        
        // Filter active overrides first
        List<PriceOverride> activeOverrides = overrides.stream()
                .filter(this::isOverrideActive)
                .collect(Collectors.toList());
        
        if (activeOverrides.isEmpty()) {
            log.debug("No active price overrides found for restaurant: {}", restaurantId);
            return index;
        }
        
        log.debug("Found {} active price overrides for restaurant", activeOverrides.size());
        
        // Batch load all mappings for active overrides in a single query (fixes N+1 problem)
        List<UUID> activeOverrideIds = activeOverrides.stream()
                .map(PriceOverride::getId)
                .collect(Collectors.toList());
        
        List<PriceOverrideMapping> allMappings = priceOverrideMappingRepository
                .findByPriceOverrideIdInWithRelations(activeOverrideIds);
        
        // Group mappings by price override ID for efficient lookup
        Map<UUID, List<PriceOverrideMapping>> mappingsByOverrideId = allMappings.stream()
                .collect(Collectors.groupingBy(m -> m.getPriceOverride().getId()));
        
        // Collect all menu category mapping IDs that need to be loaded
        List<UUID> allMenuCategoryMappingIds = allMappings.stream()
                .map(this::extractMenuCategoryMappingId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch load all menu category mappings in a single query
        Map<UUID, MenuCategoryMapping> menuCategoryMappingCache = allMenuCategoryMappingIds.isEmpty()
                ? Collections.emptyMap()
                : menuCategoryMappingRepository.findAllById(allMenuCategoryMappingIds).stream()
                        .collect(Collectors.toMap(MenuCategoryMapping::getId, Function.identity()));
        
        // Process each active override with its mappings
        for (PriceOverride priceOverride : activeOverrides) {
            List<PriceOverrideMapping> mappings = mappingsByOverrideId.getOrDefault(priceOverride.getId(), Collections.emptyList());
            
            for (PriceOverrideMapping mapping : mappings) {
                if (mapping.getRestaurant() == null || mapping.getRestaurant().getId() == null
                        || !restaurantId.equals(mapping.getRestaurant().getId())) {
                    continue;
                }

                MenuCategoryMapping menuCategoryMapping = null;
                UUID menuCategoryMappingId = extractMenuCategoryMappingId(mapping);
                if (menuCategoryMappingId != null) {
                    menuCategoryMapping = menuCategoryMappingCache.get(menuCategoryMappingId);
                    if (menuCategoryMapping == null) {
                        log.warn("MenuCategoryMapping {} not found while building price override index", menuCategoryMappingId);
                    }
                }
                if (menuCategoryMapping != null
                        && menuCategoryMapping.getCategory() != null
                        && menuCategoryMapping.getCategory().getId() != null) {
                    UUID categoryId = menuCategoryMapping.getCategory().getId();
                    PriceOverride current = index.categoryOverrides.get(categoryId);
                    if (current == null || compareRecency(current, priceOverride) < 0) {
                        index.categoryOverrides.put(categoryId, priceOverride);
                        log.debug("Added category override: categoryId={}, overrideId={}, type={}, value={}", 
                            categoryId, priceOverride.getId(), priceOverride.getOverrideType(), priceOverride.getOverrideValue());
                    }
                } else if (mapping.getMenu() != null && mapping.getMenu().getId() != null) {
                    UUID mappedMenuId = mapping.getMenu().getId();
                    PriceOverride current = index.menuOverrides.get(mappedMenuId);
                    if (current == null || compareRecency(current, priceOverride) < 0) {
                        index.menuOverrides.put(mappedMenuId, priceOverride);
                        log.debug("Added menu override: menuId={}, overrideId={}, type={}, value={}", 
                            mappedMenuId, priceOverride.getId(), priceOverride.getOverrideType(), priceOverride.getOverrideValue());
                    }
                }
            }
        }

        log.info("Built override index for restaurant {}: {} active overrides, {} category overrides, {} menu overrides", 
            restaurantId, activeOverrides.size(), index.categoryOverrides.size(), index.menuOverrides.size());

        return index;
    }

    /**
     * Resolve the effective base price by applying the most specific active price override
     * Priority: Subcategory > Category > Menu
     */
    public Double resolveEffectiveBasePrice(Double basePrice, UUID menuId, List<MenuCategoryMapping> itemMcms, ActiveOverrideIndex activeOverrideIndex) {
        if (basePrice == null || activeOverrideIndex == null) {
            log.debug("Skipping price override - basePrice or activeOverrideIndex is null");
            return basePrice;
        }

        log.debug("Resolving price override for menuId: {}, basePrice: {}, item MCMs: {}", 
            menuId, basePrice, itemMcms.size());
        log.debug("Active overrides - Category overrides: {}, Menu overrides: {}", 
            activeOverrideIndex.categoryOverrides.size(), activeOverrideIndex.menuOverrides.size());

        // Subcategory overrides take highest precedence
        Double subcategoryPrice = checkSubcategoryOverrides(basePrice, itemMcms, activeOverrideIndex);
        if (subcategoryPrice != null) {
            return subcategoryPrice;
        }

        // Category-level overrides
        Double categoryPrice = checkCategoryOverrides(basePrice, itemMcms, activeOverrideIndex);
        if (categoryPrice != null) {
            return categoryPrice;
        }

        // Menu-level override
        Double menuPrice = checkMenuOverride(basePrice, menuId, activeOverrideIndex);
        if (menuPrice != null) {
            return menuPrice;
        }

        log.debug("No price override found for this item");
        return basePrice;
    }

    /**
     * Check for subcategory-level price overrides.
     *
     * @param basePrice the base price
     * @param itemMcms list of menu category mappings
     * @param activeOverrideIndex the active override index
     * @return the new price if override found, null otherwise
     */
    private Double checkSubcategoryOverrides(Double basePrice, List<MenuCategoryMapping> itemMcms, ActiveOverrideIndex activeOverrideIndex) {
        for (MenuCategoryMapping mcm : itemMcms) {
            if (mcm == null) {
                continue;
            }
            Category category = mcm.getCategory();
            Category parent = mcm.getParentCategory();
            if (category != null && parent != null) {
                log.debug("Checking subcategory override for category: {} (parent: {})", 
                    category.getId(), parent.getId());
                PriceOverride override = activeOverrideIndex.categoryOverrides.get(category.getId());
                if (override != null) {
                    Double newPrice = applyOverride(basePrice, override.getOverrideType(), override.getOverrideValue());
                    log.info("SUBCATEGORY override applied! Category: {}, Type: {}, Value: {}, {} -> {}", 
                        category.getId(), override.getOverrideType(), override.getOverrideValue(), basePrice, newPrice);
                    return newPrice;
                }
            }
        }
        return null;
    }

    /**
     * Check for category-level price overrides.
     *
     * @param basePrice the base price
     * @param itemMcms list of menu category mappings
     * @param activeOverrideIndex the active override index
     * @return the new price if override found, null otherwise
     */
    private Double checkCategoryOverrides(Double basePrice, List<MenuCategoryMapping> itemMcms, ActiveOverrideIndex activeOverrideIndex) {
        for (MenuCategoryMapping mcm : itemMcms) {
            if (mcm == null) {
                continue;
            }
            UUID categoryId = extractCategoryId(mcm);
            if (categoryId != null) {
                log.debug("Checking category override for category: {}", categoryId);
                PriceOverride override = activeOverrideIndex.categoryOverrides.get(categoryId);
                if (override != null) {
                    Double newPrice = applyOverride(basePrice, override.getOverrideType(), override.getOverrideValue());
                    log.info("CATEGORY override applied! Category: {}, Type: {}, Value: {}, {} -> {}", 
                        categoryId, override.getOverrideType(), override.getOverrideValue(), basePrice, newPrice);
                    return newPrice;
                }
            }
        }
        return null;
    }

    /**
     * Check for menu-level price override.
     *
     * @param basePrice the base price
     * @param menuId the menu ID
     * @param activeOverrideIndex the active override index
     * @return the new price if override found, null otherwise
     */
    private Double checkMenuOverride(Double basePrice, UUID menuId, ActiveOverrideIndex activeOverrideIndex) {
        if (menuId == null) {
            return null;
        }
        log.debug("Checking menu override for menu: {}", menuId);
        PriceOverride override = activeOverrideIndex.menuOverrides.get(menuId);
        if (override != null) {
            Double newPrice = applyOverride(basePrice, override.getOverrideType(), override.getOverrideValue());
            log.info("MENU override applied! Menu: {}, Type: {}, Value: {}, {} -> {}", 
                menuId, override.getOverrideType(), override.getOverrideValue(), basePrice, newPrice);
            return newPrice;
        }
        return null;
    }

    /**
     * Extract category ID from menu category mapping.
     *
     * @param mcm the menu category mapping
     * @return the category ID, or null if not found
     */
    private UUID extractCategoryId(MenuCategoryMapping mcm) {
        if (mcm.getParentCategory() != null) {
            return mcm.getParentCategory().getId();
        } else if (mcm.getCategory() != null) {
            return mcm.getCategory().getId();
        }
        return null;
    }

    /**
     * Check if price override is currently active (LIVE status)
     * Uses validFrom and validTo directly
     */
    public boolean isOverrideActive(PriceOverride priceOverride) {
        if (priceOverride == null || Boolean.TRUE.equals(priceOverride.getIsDeleted()) || priceOverride.getStatus() != PriceOverrideStatus.LIVE) {
            return false;
        }

        OffsetDateTime nowUtc = OffsetDateTime.now(UTC);

        // Check if validFrom is in the future
        if (priceOverride.getValidFrom() != null && nowUtc.isBefore(priceOverride.getValidFrom())) {
            return false; // Not yet started
        }

        // Check if validTo is in the past
        return priceOverride.getValidTo() == null || !nowUtc.isAfter(priceOverride.getValidTo());
    }

    /**
     * Compare two price overrides by recency (for choosing most recent when multiple apply)
     */
    private int compareRecency(PriceOverride existing, PriceOverride candidate) {
        java.time.OffsetDateTime existingTimestamp = existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt();
        java.time.OffsetDateTime candidateTimestamp = candidate.getUpdatedAt() != null ? candidate.getUpdatedAt() : candidate.getCreatedAt();

        if (existingTimestamp == null && candidateTimestamp == null) {
            return 0;
        }
        if (existingTimestamp == null) {
            return -1;
        }
        if (candidateTimestamp == null) {
            return 1;
        }
        return existingTimestamp.compareTo(candidateTimestamp);
    }

    /**
     * Apply a price override to the base price based on override type
     */
    private Double applyOverride(Double basePrice, OverrideType overrideType, BigDecimal overrideValue) {
        if (basePrice == null) {
            return 0.0;
        }

        BigDecimal base = BigDecimal.valueOf(basePrice);
        switch (overrideType) {
            case PERCENTAGE_INCREMENT:
                return base.multiply(BigDecimal.ONE.add(overrideValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            case PERCENTAGE_DECREMENT:
                return base.multiply(BigDecimal.ONE.subtract(overrideValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            case AMOUNT_INCREMENT:
                return base.add(overrideValue)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            case AMOUNT_DECREMENT:
                BigDecimal decremented = base.subtract(overrideValue);
                if (decremented.compareTo(BigDecimal.ZERO) < 0) {
                    decremented = BigDecimal.ZERO;
                }
                return decremented.setScale(2, RoundingMode.HALF_UP).doubleValue();
            default:
                return base.setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    private UUID extractMenuCategoryMappingId(PriceOverrideMapping mapping) {
        if (mapping == null) {
            return null;
        }
        // Use Lombok-generated getter directly
        return mapping.getMenuCategoryMappingId();
    }
}

