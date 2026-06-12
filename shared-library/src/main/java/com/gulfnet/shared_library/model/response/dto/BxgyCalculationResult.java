package com.gulfnet.shared_library.model.response.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of BXGY discount calculation for subtotal
 */
public class BxgyCalculationResult {
    private final BigDecimal totalPrice;
    private final Map<UUID, BigDecimal> itemPrices;
    private final Map<UUID, BigDecimal> getItemPrices; // Separate map for get item prices (for items that are both buy and get)
    private final List<BxgyDiscountInfo> appliedBxgyDiscounts;
    private final Map<String, Integer> paidQuantitiesByRequest; // Per-request paid quantity: key = "itemId:quantity:isBuyItem:isGetItem"
    /**
     * Effective (scaled) item amounts extracted from combos for alcoholic/non-alcoholic tax breakdown.
     * This enables combo items to participate in tax split without changing combo price or tax logic.
     */
    private final List<ComboTaxItem> comboTaxItems;
    /**
     * BXGY discount information for each item request.
     * Key = "itemId:quantity:isBuyItem:isGetItem", Value = BXGY info (discountApplicationId, discountId, bxgyRole, freeQuantity)
     */
    private final Map<String, BxgyItemInfo> bxgyInfoByRequest;
    /**
     * Aggregated discount usages (item/category/BXGY) calculated during subtotal computation.
     * These will later be persisted to OrderDiscountUsage by consumers.
     */
    private final List<DiscountUsageSummary> discountUsages;

    /**
     * Constructor with both combo tax items and bxgy info by request (full version).
     */
    public BxgyCalculationResult(BigDecimal totalPrice, Map<UUID, BigDecimal> itemPrices,
                                Map<UUID, BigDecimal> getItemPrices,
                                List<BxgyDiscountInfo> appliedBxgyDiscounts,
                                Map<String, Integer> paidQuantitiesByRequest,
                                List<ComboTaxItem> comboTaxItems,
                                Map<String, BxgyItemInfo> bxgyInfoByRequest,
                                List<DiscountUsageSummary> discountUsages) {
        this.totalPrice = totalPrice;
        this.itemPrices = itemPrices;
        this.getItemPrices = getItemPrices;
        this.appliedBxgyDiscounts = appliedBxgyDiscounts;
        this.paidQuantitiesByRequest = paidQuantitiesByRequest != null ? paidQuantitiesByRequest : new HashMap<>();
        this.comboTaxItems = comboTaxItems != null ? comboTaxItems : new ArrayList<>();
        this.bxgyInfoByRequest = bxgyInfoByRequest != null ? bxgyInfoByRequest : new HashMap<>();
        this.discountUsages = discountUsages != null ? discountUsages : new ArrayList<>();
    }

    /**
     * Constructor with discount usage summary and bxgy info (development version).
     */
    public BxgyCalculationResult(BigDecimal totalPrice, Map<UUID, BigDecimal> itemPrices, 
                                Map<UUID, BigDecimal> getItemPrices, 
                                List<BxgyDiscountInfo> appliedBxgyDiscounts, 
                                Map<String, Integer> paidQuantitiesByRequest,
                                Map<String, BxgyItemInfo> bxgyInfoByRequest,
                                List<DiscountUsageSummary> discountUsages) {
        this(totalPrice, itemPrices, getItemPrices, appliedBxgyDiscounts, paidQuantitiesByRequest, null, bxgyInfoByRequest, discountUsages);
    }

    /**
     * Constructor with combo tax items (HEAD version).
     */
    public BxgyCalculationResult(BigDecimal totalPrice, Map<UUID, BigDecimal> itemPrices,
                                Map<UUID, BigDecimal> getItemPrices,
                                List<BxgyDiscountInfo> appliedBxgyDiscounts,
                                Map<String, Integer> paidQuantitiesByRequest,
                                List<ComboTaxItem> comboTaxItems,
                                List<DiscountUsageSummary> discountUsages) {
        this(totalPrice, itemPrices, getItemPrices, appliedBxgyDiscounts, paidQuantitiesByRequest, comboTaxItems, new HashMap<>(), discountUsages);
    }

    /**
     * Backward compatible constructor without discount usage summary
     * @deprecated Use the constructor with discountUsages parameter for new code
     */
    @Deprecated
    public BxgyCalculationResult(BigDecimal totalPrice, Map<UUID, BigDecimal> itemPrices, 
                                Map<UUID, BigDecimal> getItemPrices, 
                                List<BxgyDiscountInfo> appliedBxgyDiscounts, 
                                Map<String, Integer> paidQuantitiesByRequest) {
        this(totalPrice, itemPrices, getItemPrices, appliedBxgyDiscounts, paidQuantitiesByRequest, null, new HashMap<>(), new ArrayList<>());
    }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public Map<UUID, BigDecimal> getItemPrices() { return itemPrices; }
    public Map<UUID, BigDecimal> getGetItemPrices() { return getItemPrices; }
    public List<BxgyDiscountInfo> getAppliedBxgyDiscounts() { return appliedBxgyDiscounts; }
    public Map<String, Integer> getPaidQuantitiesByRequest() { return paidQuantitiesByRequest; }
    public List<ComboTaxItem> getComboTaxItems() { return comboTaxItems; }
    public Map<String, BxgyItemInfo> getBxgyInfoByRequest() { return bxgyInfoByRequest; }
    public List<DiscountUsageSummary> getDiscountUsages() { return discountUsages; }
}

