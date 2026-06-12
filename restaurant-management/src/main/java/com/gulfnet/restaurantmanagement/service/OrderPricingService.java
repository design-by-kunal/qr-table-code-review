package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public interface OrderPricingService {

    OrderCalculationResult calculateCompleteOrderTotals(
            List<OrderedItemRequest> orderedItems,
            List<OrderedComboRequest> orderedCombos,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            Discount orderDiscount,
            BigDecimal additionalDiscountValue,
            DiscountType additionalDiscountType,
            OrderType orderType,
            Locale userLocale);

    OrderCalculationResult recalculateTotalsFromSubtotal(
            BigDecimal subTotal,
            Discount orderDiscount,
            BigDecimal additionalDiscountValue,
            DiscountType additionalDiscountType,
            OrderType orderType,
            Locale userLocale,
            BxgyCalculationResult bxgyResult);

    BxgyCalculationResult calculateSubTotalWithBxgyDiscounts(
            List<OrderedItemRequest> orderedItems,
            List<OrderedComboRequest> orderedCombos,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex);

    BxgyDiscountInfo applyBxgyDiscount(
            List<OrderedItemRequest> buyItems,
            List<OrderedItemRequest> getItems,
            Discount discount,
            Map<UUID, DiscountCalculationResult> itemDiscountResults,
            Map<Integer, DiscountCalculationResult> itemDiscountResultsByIndex,
            List<OrderedItemRequest> allOrderedItems,
            Map<UUID, com.gulfnet.shared_library.entity.CategoryItemMapping> categoryMappingCache,
            GetItemPricesHolder getItemPricesHolder,
            Map<String, BxgyItemInfo> bxgyInfoByRequest);

    OrderDiscountResult applyOrderLevelDiscount(Discount orderDiscount, BigDecimal subTotal, Locale userLocale);

    com.gulfnet.shared_library.entity.CategoryItemMapping getCategoryItemMapping(UUID menuId, UUID itemId);

    DiscountCalculationResult calculateItemPriceWithOverride(
            UUID menuId,
            UUID itemId,
            Integer quantity,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex);

    /**
     * Overloaded method that accepts pre-loaded Item to avoid duplicate queries
     * Used for batch processing in order creation
     */
    DiscountCalculationResult calculateItemPriceWithOverride(
            UUID menuId,
            Item item,
            Integer quantity,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex);

    DiscountCalculationResult calculateItemPrice(UUID menuId, UUID itemId, Integer quantity);

    DiscountCalculationResult calculateItemLevelDiscount(
            UUID menuId,
            com.gulfnet.shared_library.entity.CategoryItemMapping categoryItemMapping,
            BigDecimal basePrice,
            Integer quantity);

    DiscountCalculationResult calculateItemLevelDiscount(
            UUID menuId,
            com.gulfnet.shared_library.entity.CategoryItemMapping categoryItemMapping,
            BigDecimal basePrice,
            Integer quantity,
            UUID restaurantId);

    DiscountCalculationResult calculateCategoryLevelDiscount(
            UUID menuId,
            com.gulfnet.shared_library.entity.MenuCategoryMapping menuCategoryMapping,
            BigDecimal basePrice,
            Integer quantity);

    DiscountCalculationResult calculateCategoryLevelDiscount(
            UUID menuId,
            com.gulfnet.shared_library.entity.MenuCategoryMapping menuCategoryMapping,
            BigDecimal basePrice,
            Integer quantity,
            UUID restaurantId);

    DiscountCalculationResult calculateCategoryLevelDiscountForItem(
            UUID menuId,
            UUID itemId,
            BigDecimal basePrice,
            Integer quantity);

    DiscountCalculationResult calculateCategoryLevelDiscountForItem(
            UUID menuId,
            UUID itemId,
            BigDecimal basePrice,
            Integer quantity,
            UUID restaurantId);

    BigDecimal calculateDiscountedPrice(BigDecimal basePrice, Discount discount, Integer quantity);

    DiscountCalculationResult chooseBestDiscount(
            DiscountCalculationResult itemDiscount,
            DiscountCalculationResult categoryDiscount,
            BigDecimal basePrice,
            Integer quantity);

    ItemPriceCalculationResult calculateItemPriceWithModifiers(
            Item item,
            OrderedItemRequest itemRequest,
            BigDecimal discountedItemPrice,
            BigDecimal overriddenBasePricePerUnit,
            boolean hasDiscount,
            boolean isBxgyGetItem,
            Map<String, Integer> paidQuantitiesByRequest,
            Locale userLocale,
            DiscountCalculationResult discountResult,
            Map<UUID, BigDecimal> itemPrices);

    List<UUID> ensureBxgyDiscountIdsIncluded(OrderedItemRequest itemRequest, UUID menuId);

    BigDecimal calculateChoiceComboPrice(com.gulfnet.shared_library.entity.Combo combo, OrderedComboRequest comboRequest, Locale userLocale);

    BigDecimal calculateMixedComboPrice(com.gulfnet.shared_library.entity.Combo combo, OrderedComboRequest comboRequest, Locale userLocale);

    class GetItemPricesHolder {
        private final Map<UUID, BigDecimal> prices = new java.util.HashMap<>();
        // Store per-request paid quantity info: key = "itemId:quantity:isBuyItem:isGetItem", value = paidQuantity
        private final Map<String, Integer> paidQuantitiesByRequest = new java.util.HashMap<>();

        public Map<UUID, BigDecimal> getPrices() {
            return prices;
        }

        public Map<String, Integer> getPaidQuantitiesByRequest() {
            return paidQuantitiesByRequest;
        }
    }
}

