package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.OrderPricingService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.ComboGroupType;
import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.model.request.OrderedComboGroupRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPricingServiceImpl implements OrderPricingService {

    private static final String MSG_ITEM_NOT_FOUND = "item.not.found";
    private static final String MSG_MODIFIER_ITEM_NOT_FOUND = "modifier.item.not.found";

    private final ItemRepository itemRepository;
    private final DiscountRepository discountRepository;
    private final MenuRepository menuRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final ItemDiscountMappingRepository itemDiscountMappingRepository;
    private final CategoryDiscountMappingRepository categoryDiscountMappingRepository;
    private final MenuDiscountMappingRepository menuDiscountMappingRepository;
    private final DiscountBxgyItemRepository discountBxgyItemRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final ComboRepository comboRepository;
    private final ComboGroupRepository comboGroupRepository;
    private final ComboItemModifierRepository comboItemModifierRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final PriceOverrideHelper priceOverrideHelper;
    private final OrderValidationService orderValidationService;
    private final com.gulfnet.restaurantmanagement.util.MessageUtil messageUtil;

    /**
     * Java divide rounding for intermediate monetary math; matches chain {@code restaurant.chain.roundingMode}
     * when set, otherwise {@link CurrencyFormatter#getDefaultRoundingPolicy()}.
     */
    private java.math.RoundingMode configuredDivideRoundingMode() {
        com.gulfnet.shared_library.enums.RoundingMode policy = null;
        if (restaurantChainConfigProperties.getChain() != null) {
            policy = restaurantChainConfigProperties.getChain().getRoundingMode();
        }
        if (policy == null) {
            policy = CurrencyFormatter.getDefaultRoundingPolicy();
        }
        return CurrencyFormatter.resolveRoundingMode(policy);
    }

    /**
     * Splits a charge (service/packing) across alcoholic vs non-alcoholic using item subtotals.
     * Avoids {@code charge × ratio} drift when ratios are stored at 10 dp (e.g. 210×(600/2100) → 59.999… → ¥659
     * after truncate instead of ¥60 → ¥660).
     */
    private BigDecimal[] splitChargeByItemSubtotals(BigDecimal totalCharge,
            BigDecimal alcoholicSubtotal,
            BigDecimal nonAlcoholicSubtotal) {
        if (totalCharge == null || totalCharge.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
        }
        BigDecimal denom = alcoholicSubtotal.add(nonAlcoholicSubtotal);
        if (denom.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
        }
        java.math.RoundingMode rm = configuredDivideRoundingMode();
        BigDecimal alcoholicShare = totalCharge.multiply(alcoholicSubtotal).divide(denom, 20, rm);
        BigDecimal nonAlcoholicShare = totalCharge.subtract(alcoholicShare);
        return new BigDecimal[] { alcoholicShare, nonAlcoholicShare };
    }

    // Helper class for alcoholic breakdown
    private static class AlcoholicBreakdown {
        BigDecimal alcoholicSubtotal;
        BigDecimal nonAlcoholicSubtotal;
        BigDecimal alcoholicRatio;
        BigDecimal nonAlcoholicRatio;
        
        AlcoholicBreakdown(BigDecimal alcoholicSubtotal, BigDecimal nonAlcoholicSubtotal, 
                          BigDecimal alcoholicRatio, BigDecimal nonAlcoholicRatio) {
            this.alcoholicSubtotal = alcoholicSubtotal;
            this.nonAlcoholicSubtotal = nonAlcoholicSubtotal;
            this.alcoholicRatio = alcoholicRatio;
            this.nonAlcoholicRatio = nonAlcoholicRatio;
        }
    }

    /**
     * Internal holder for raw (unscaled) combo item amounts per unit, used to compute effective
     * item amounts inside combos for alcoholic/non-alcoholic tax breakdown.
     */
    private static class RawComboTaxItem {
        private final BigDecimal rawAmountPerUnit;
        private final AlcoholType alcoholType;

        private RawComboTaxItem(BigDecimal rawAmountPerUnit, AlcoholType alcoholType) {
            this.rawAmountPerUnit = rawAmountPerUnit;
            this.alcoholType = alcoholType;
        }
    }

    // ==================== PUBLIC INTERFACE METHODS ====================

    /**
     * Calculates the complete set of monetary totals for an order draft.
     * <p>
     * This is the primary pricing entry-point used during order creation/update. It:
     * </p>
     * <ul>
     *   <li>Splits incoming requests into existing (persisted) vs new (not yet persisted) items/combos.</li>
     *   <li>Uses stored discounted totals for existing entities to avoid recomputation drift.</li>
     *   <li>Computes subtotal for new entities including item/category discounts and BXGY (buy-X-get-Y) logic.</li>
     *   <li>Applies an optional order-level discount (PERCENT/FLAT; BXGY is item-level only).</li>
     *   <li>Calculates tax, service charge (dine-in) or packing charge (takeaway), and an optional additional discount.</li>
     *   <li>Builds alcoholic/non-alcoholic taxable/tax breakdown where possible.</li>
     * </ul>
     *
     * @param orderedItems         ordered item requests (may include existing items via {@code orderedItemId})
     * @param orderedCombos        ordered combo requests (may include existing combos via {@code orderedComboId})
     * @param menuId               menu used for pricing/discount validation
     * @param restaurantId         restaurant context (used for discount validity and price overrides)
     * @param activeOverrideIndex  resolved active overrides for price override calculations (optional)
     * @param orderDiscount        optional order-level discount (PERCENT/FLAT supported here)
     * @param additionalDiscountValue optional additional discount value applied after charges/taxes (optional)
     * @param additionalDiscountType  additional discount type (PERCENT/FLAT) (optional)
     * @param orderType            dine-in vs takeaway charge rules
     * @param userLocale           locale for localized exception messages
     * @return calculated subtotal, discounts, charges, taxes, and final total (plus BXGY application details)
     * @throws ResponseStatusException when required entities are missing or validation fails
     */
    @Override
    public OrderCalculationResult calculateCompleteOrderTotals(
            List<OrderedItemRequest> orderedItems,
            List<OrderedComboRequest> orderedCombos,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            Discount orderDiscount,
            BigDecimal additionalDiscountValue,
            DiscountType additionalDiscountType,
            OrderType orderType,
            Locale userLocale) {
        
        // ==================== STEP 1: CALCULATE SUBTOTAL WITH ITEM/CATEGORY/BXGY DISCOUNTS ====================
        // Separate existing items/combos (with IDs) from new ones (without IDs)
        BigDecimal storedItemsSubTotal = BigDecimal.ZERO;
        BigDecimal storedCombosSubTotal = BigDecimal.ZERO;
        List<OrderedItemRequest> newItems = new ArrayList<>();
        List<OrderedComboRequest> newCombos = new ArrayList<>();
        // For alcoholic/non-alcoholic tax breakdown we need effective combo item amounts.
        // When combos are "existing" (have orderedComboId), we won't compute them via BXGY subtotal,
        // so we derive comboTaxItems directly from stored ordered_combo -> ordered_item relationships.
        List<ComboTaxItem> existingComboTaxItemsForBreakdown = new ArrayList<>();

        // Batch-load existing ordered items/combos once.
        // This removes N+1 DB calls from the loops below.
        Set<UUID> existingOrderedItemIds = Collections.emptySet();
        Map<UUID, OrderedItem> existingOrderedItemsById = Collections.emptyMap();
        if (orderedItems != null && !orderedItems.isEmpty()) {
            existingOrderedItemIds = orderedItems.stream()
                    .map(OrderedItemRequest::getOrderedItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!existingOrderedItemIds.isEmpty()) {
                existingOrderedItemsById = orderedItemRepository.findAllById(existingOrderedItemIds).stream()
                        .collect(Collectors.toMap(OrderedItem::getId, oi -> oi));
            }
        }

        Set<UUID> existingOrderedComboIds = Collections.emptySet();
        Map<UUID, OrderedCombo> existingOrderedCombosById = Collections.emptyMap();
        if (orderedCombos != null && !orderedCombos.isEmpty()) {
            existingOrderedComboIds = orderedCombos.stream()
                    .map(OrderedComboRequest::getOrderedComboId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!existingOrderedComboIds.isEmpty()) {
                existingOrderedCombosById = orderedComboRepository.findAllById(existingOrderedComboIds).stream()
                        .collect(Collectors.toMap(OrderedCombo::getId, oc -> oc));
            }
        }
        
        // Process items - use stored amounts for existing items, calculate for new ones
        if (orderedItems != null && !orderedItems.isEmpty()) {
            for (OrderedItemRequest itemRequest : orderedItems) {
                if (itemRequest.getOrderedItemId() != null) {
                    // Existing item - fetch stored amount from preloaded DB rows
                    OrderedItem existingItem = existingOrderedItemsById.get(itemRequest.getOrderedItemId());
                    if (existingItem != null) {
                        if (existingItem.getItemStatus() == ItemStatus.CANCELED) {
                            continue;
                        }
                        BigDecimal fallbackAmount = existingItem.getTotalItemAmount() != null
                                ? existingItem.getTotalItemAmount() : BigDecimal.ZERO;
                        BigDecimal itemAmount = existingItem.getTotalDiscountedItemAmount() != null
                                ? existingItem.getTotalDiscountedItemAmount()
                                : fallbackAmount;
                        storedItemsSubTotal = storedItemsSubTotal.add(itemAmount);
                        log.debug("Using stored amount for existing item {}: {}", itemRequest.getOrderedItemId(), itemAmount);
                    }
                } else {
                    // New item - will be calculated
                    newItems.add(itemRequest);
                }
            }
        }
        
        // Process combos - use stored amounts for existing combos, calculate for new ones
        if (orderedCombos != null && !orderedCombos.isEmpty()) {
            for (OrderedComboRequest comboRequest : orderedCombos) {
                if (comboRequest.getOrderedComboId() != null) {
                    // Existing combo - fetch stored amount from preloaded DB rows
                    OrderedCombo existingCombo = existingOrderedCombosById.get(comboRequest.getOrderedComboId());
                    if (existingCombo != null) {
                        if (existingCombo.getItemStatus() == ItemStatus.CANCELED) {
                            continue;
                        }
                        BigDecimal fallbackPrice = existingCombo.getPrice() != null
                                ? existingCombo.getPrice() : BigDecimal.ZERO;
                        BigDecimal comboAmount = existingCombo.getTotalComboAmount() != null
                                ? existingCombo.getTotalComboAmount()
                                : fallbackPrice;
                        storedCombosSubTotal = storedCombosSubTotal.add(comboAmount);
                        log.debug("Using stored amount for existing combo {}: {}", comboRequest.getOrderedComboId(), comboAmount);
                    
                    // Build combo tax items from stored combo's ordered items (to keep alcoholic split accurate).
                    // We use stored discounted amounts per ordered item as the effective combo item amounts.
                    List<OrderedItem> comboOrderedItems = existingCombo.getOrderedItems();
                    if (comboOrderedItems != null && !comboOrderedItems.isEmpty()) {
                        for (OrderedItem comboOrderedItem : comboOrderedItems) {
                            if (comboOrderedItem == null
                                    || (comboOrderedItem.getItemStatus() != null && comboOrderedItem.getItemStatus() == ItemStatus.CANCELED)) {
                                continue;
                            }
                            OrderedItem nonNullComboOrderedItem = comboOrderedItem;
                            BigDecimal itemAmount = BigDecimal.ZERO;
                            if (nonNullComboOrderedItem.getTotalDiscountedItemAmount() != null) {
                                itemAmount = nonNullComboOrderedItem.getTotalDiscountedItemAmount();
                            } else if (nonNullComboOrderedItem.getTotalItemAmount() != null) {
                                itemAmount = nonNullComboOrderedItem.getTotalItemAmount();
                            } else if (nonNullComboOrderedItem.getPrice() != null && nonNullComboOrderedItem.getQuantity() != null) {
                                itemAmount = nonNullComboOrderedItem.getPrice().multiply(BigDecimal.valueOf(nonNullComboOrderedItem.getQuantity()));
                            }
                            
                            AlcoholType alcoholType = nonNullComboOrderedItem.getAlcoholType();
                            if (alcoholType == null && nonNullComboOrderedItem.getItem() != null) {
                                alcoholType = nonNullComboOrderedItem.getItem().getAlcoholType();
                            }
                            if (alcoholType == null) {
                                alcoholType = AlcoholType.NON_ALCOHOLIC;
                            }
                            
                            existingComboTaxItemsForBreakdown.add(new ComboTaxItem(itemAmount, alcoholType));
                        }
                    }
                    }
                } else {
                    // New combo - will be calculated
                    newCombos.add(comboRequest);
                }
            }
        }
        
        // Calculate subtotal for new items/combos only
        BigDecimal calculatedSubTotal = BigDecimal.ZERO;
        BxgyCalculationResult bxgyResult = null;
        
        if (!newItems.isEmpty() || !newCombos.isEmpty()) {
            // Only calculate for new items/combos
            bxgyResult = calculateSubTotalWithBxgyDiscounts(
                    newItems.isEmpty() ? null : newItems, 
                    newCombos.isEmpty() ? null : newCombos, 
                    menuId,
                    restaurantId,
                    activeOverrideIndex);
            calculatedSubTotal = bxgyResult.getTotalPrice();
            log.debug("Calculated subtotal for new items/combos: {} ({} new items, {} new combos)", 
                    calculatedSubTotal, newItems.size(), newCombos.size());
        } else {
            // No new items/combos - create empty BXGY result
            bxgyResult = new BxgyCalculationResult(
                    BigDecimal.ZERO, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
        
        // Combine stored amounts with calculated amounts
        BigDecimal subTotal = storedItemsSubTotal.add(storedCombosSubTotal).add(calculatedSubTotal);
        log.debug("Total subtotal - Stored items: {}, Stored combos: {}, Calculated: {}, Total: {}", 
                storedItemsSubTotal, storedCombosSubTotal, calculatedSubTotal, subTotal);
        
        // ==================== STEP 2: APPLY ORDER-LEVEL DISCOUNT ====================
        OrderDiscountResult orderDiscountResult = applyOrderLevelDiscount(orderDiscount, subTotal, userLocale);
        BigDecimal subtotalAfterDiscount = orderDiscountResult.getFinalSubTotal(); // Amount after order discount
        BigDecimal orderDiscountSavings = orderDiscountResult.getDiscountSavings();
        
        // ==================== STEP 3 & 4: CALCULATE TAXES AND CHARGES ====================
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        
        // Calculate alcoholic and non-alcoholic subtotals (items + combo effective item amounts)
        // Combine comboTaxItems produced by BXGY calculation (new combos) with combo tax items derived
        // from stored combos (existing combos).
        List<ComboTaxItem> comboTaxItemsForBreakdown = new ArrayList<>();
        if (bxgyResult != null && bxgyResult.getComboTaxItems() != null) {
            comboTaxItemsForBreakdown.addAll(bxgyResult.getComboTaxItems());
        }
        if (existingComboTaxItemsForBreakdown != null && !existingComboTaxItemsForBreakdown.isEmpty()) {
            comboTaxItemsForBreakdown.addAll(existingComboTaxItemsForBreakdown);
        }
        
        AlcoholicBreakdown alcoholicBreakdown = calculateAlcoholicBreakdown(
            orderedItems, subtotalAfterDiscount, bxgyResult, comboTaxItemsForBreakdown.isEmpty() ? null : comboTaxItemsForBreakdown,
            menuId, restaurantId, activeOverrideIndex);
        
        // Get currency for formatting
        String currency = chainConfig.getCurrency();
        
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        BigDecimal packingChargeAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxAmount = BigDecimal.ZERO;
        BigDecimal nonAlcoholicTaxAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxableAmount = BigDecimal.ZERO;
        BigDecimal nonAlcoholicTaxableAmount = BigDecimal.ZERO;
        
        if (orderType == OrderType.DINE_IN) {
            // Dine In: Service Charge on Subtotal, Tax on (Subtotal + Service Charge)
            RestaurantChainConfigProperties.ServiceChargesForDineIn dineInServiceCharge = 
                chainConfig.getServiceChargesForDineIn();
            // Calculate service charge WITHOUT formatting first (for accurate tax base calculation)
            BigDecimal unformattedServiceChargeAmount = BigDecimal.ZERO;
            if (dineInServiceCharge != null) {
                unformattedServiceChargeAmount = calculateChargeAmountUnformatted(
                    subtotalAfterDiscount, 
                    BigDecimal.valueOf(dineInServiceCharge.getValue()),
                    dineInServiceCharge.getType());
                // Format service charge for storage/display
                serviceChargeAmount = CurrencyFormatter.formatAmount(unformattedServiceChargeAmount, currency);
            }
            
            // Calculate tax separately for alcoholic and non-alcoholic items
            // Use UNFORMATTED service charge for accurate tax base calculation (split without 10dp ratio drift)
            BigDecimal[] serviceSplit = splitChargeByItemSubtotals(
                    unformattedServiceChargeAmount,
                    alcoholicBreakdown.alcoholicSubtotal,
                    alcoholicBreakdown.nonAlcoholicSubtotal);
            BigDecimal alcoholicTaxBase = alcoholicBreakdown.alcoholicSubtotal.add(serviceSplit[0]);
            BigDecimal nonAlcoholicTaxBase = alcoholicBreakdown.nonAlcoholicSubtotal.add(serviceSplit[1]);

            // Taxable bases are what consumption tax is calculated from.
            // Store formatted values for stable persistence/display.
            // IMPORTANT: format + reconcile to avoid +/-1 roundoff drift when both alcoholic and non-alcoholic exist.
            BigDecimal totalTaxableBaseFormatted = CurrencyFormatter.formatAmount(
                alcoholicTaxBase.add(nonAlcoholicTaxBase), currency);
            alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxBase, currency);
            nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);
            
            // Ensure tax setup exists
            if (chainConfig.getTaxSetup() != null && chainConfig.getTaxSetup().getDineIn() != null) {
                RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge = 
                    chainConfig.getTaxSetup().getDineIn().getAlcoholic();
                RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge = 
                    chainConfig.getTaxSetup().getDineIn().getNonAlcoholic();
                
                // Per-bucket tax formatting (chain rounding). Avoid format(alc+non) − formatted(alc) on the
                // non-alcoholic line — that inflates non-alcoholic tax when both buckets truncate (e.g. 173−49=124 vs trunc(123.7)=123).
                BigDecimal alcoholicTaxUnformatted = BigDecimal.ZERO;
                BigDecimal nonAlcoholicTaxUnformatted = BigDecimal.ZERO;

                if (alcoholicTaxCharge != null && alcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                    alcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        alcoholicTaxBase,
                        BigDecimal.valueOf(alcoholicTaxCharge.getValue()),
                        alcoholicTaxCharge.getType());
                }

                if (nonAlcoholicTaxCharge != null && nonAlcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                    nonAlcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        nonAlcoholicTaxBase,
                        BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()),
                        nonAlcoholicTaxCharge.getType());
                }

                alcoholicTaxAmount = CurrencyFormatter.formatAmount(alcoholicTaxUnformatted, currency);
                nonAlcoholicTaxAmount = CurrencyFormatter.formatAmount(nonAlcoholicTaxUnformatted, currency);

                // For display/persistence: infer alcoholic taxable from bucket tax and rate when both buckets
                // use PERCENT. Use unformatted tax here — deriving from currency-rounded tax would understate
                // the alcoholic base (e.g. 5.5 → tax ¥5 → implied ¥50 instead of ¥55) and push the remainder
                // into non-alcoholic taxable even when all items are alcoholic.
                if (alcoholicTaxCharge != null && nonAlcoholicTaxCharge != null
                        && alcoholicTaxCharge.getType() == ChargeType.PERCENT
                        && nonAlcoholicTaxCharge.getType() == ChargeType.PERCENT
                        && alcoholicTaxCharge.getValue() != 0
                        && nonAlcoholicTaxCharge.getValue() != 0) {
                    BigDecimal alcoholicTaxableDerived = alcoholicTaxUnformatted
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(alcoholicTaxCharge.getValue()), 10, configuredDivideRoundingMode());
                    alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxableDerived, currency);
                    nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);
                }

                log.debug("DINE_IN - Tax calculated (reconciled): alcBase={}, alcRate={}, alcType={}, alcTaxUnf={}, alcTax={}, nonAlcBase={}, nonAlcRate={}, nonAlcType={}, nonAlcTaxUnf={}, nonAlcTax={}, totalTax={}",
                    alcoholicTaxBase,
                    alcoholicTaxCharge != null ? alcoholicTaxCharge.getValue() : null,
                    alcoholicTaxCharge != null ? alcoholicTaxCharge.getType() : null,
                    alcoholicTaxUnformatted,
                    alcoholicTaxAmount,
                    nonAlcoholicTaxBase,
                    nonAlcoholicTaxCharge != null ? nonAlcoholicTaxCharge.getValue() : null,
                    nonAlcoholicTaxCharge != null ? nonAlcoholicTaxCharge.getType() : null,
                    nonAlcoholicTaxUnformatted,
                    nonAlcoholicTaxAmount,
                    alcoholicTaxAmount.add(nonAlcoholicTaxAmount));
            } else {
                log.warn("Tax setup not configured for DINE_IN order type");
            }
            
            taxAmount = alcoholicTaxAmount.add(nonAlcoholicTaxAmount);
            log.info("DINE_IN Tax Calculation - Alcoholic: {}, Non-alcoholic: {}, Total: {}", 
                alcoholicTaxAmount, nonAlcoholicTaxAmount, taxAmount);
        } else {
            // Takeaway: Packaging Charge on Subtotal, Tax on (Subtotal + Packaging Charge)
            RestaurantChainConfigProperties.PackingChargesForTakeaway packingCharges = 
                chainConfig.getPackingChargesForTakeaway();
            // Calculate packing charge WITHOUT formatting first (for accurate tax base calculation)
            BigDecimal unformattedPackingChargeAmount = calculateChargeAmountUnformatted(
                subtotalAfterDiscount,
                BigDecimal.valueOf(packingCharges.getValue()),
                packingCharges.getType());
            // Format packing charge for storage/display
            packingChargeAmount = CurrencyFormatter.formatAmount(unformattedPackingChargeAmount, currency);
            
            // Calculate tax separately for alcoholic and non-alcoholic items
            // Use UNFORMATTED packing charge for accurate tax base calculation (split without 10dp ratio drift)
            BigDecimal[] packingSplit = splitChargeByItemSubtotals(
                    unformattedPackingChargeAmount,
                    alcoholicBreakdown.alcoholicSubtotal,
                    alcoholicBreakdown.nonAlcoholicSubtotal);
            BigDecimal alcoholicTaxBase = alcoholicBreakdown.alcoholicSubtotal.add(packingSplit[0]);
            BigDecimal nonAlcoholicTaxBase = alcoholicBreakdown.nonAlcoholicSubtotal.add(packingSplit[1]);

            // Taxable bases are what consumption tax is calculated from.
            // Store formatted values for stable persistence/display.
            // IMPORTANT: format + reconcile to avoid +/-1 roundoff drift when both alcoholic and non-alcoholic exist.
            BigDecimal totalTaxableBaseFormatted = CurrencyFormatter.formatAmount(
                alcoholicTaxBase.add(nonAlcoholicTaxBase), currency);
            alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxBase, currency);
            nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);
            
            // Ensure tax setup exists
            if (chainConfig.getTaxSetup() != null && chainConfig.getTaxSetup().getTakeAway() != null) {
                RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge = 
                    chainConfig.getTaxSetup().getTakeAway().getAlcoholic();
                RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge = 
                    chainConfig.getTaxSetup().getTakeAway().getNonAlcoholic();
                
                // Per-bucket tax formatting (same as DINE_IN; no combined-then-split reconciliation).
                BigDecimal alcoholicTaxUnformatted = BigDecimal.ZERO;
                BigDecimal nonAlcoholicTaxUnformatted = BigDecimal.ZERO;

                if (alcoholicTaxCharge != null && alcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                    alcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        alcoholicTaxBase,
                        BigDecimal.valueOf(alcoholicTaxCharge.getValue()),
                        alcoholicTaxCharge.getType());
                }

                if (nonAlcoholicTaxCharge != null && nonAlcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                    nonAlcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        nonAlcoholicTaxBase,
                        BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()),
                        nonAlcoholicTaxCharge.getType());
                }

                alcoholicTaxAmount = CurrencyFormatter.formatAmount(alcoholicTaxUnformatted, currency);
                nonAlcoholicTaxAmount = CurrencyFormatter.formatAmount(nonAlcoholicTaxUnformatted, currency);

                // Same rationale as DINE_IN: derive implied alcoholic taxable from unformatted bucket tax.
                if (alcoholicTaxCharge != null && nonAlcoholicTaxCharge != null
                        && alcoholicTaxCharge.getType() == ChargeType.PERCENT
                        && nonAlcoholicTaxCharge.getType() == ChargeType.PERCENT
                        && alcoholicTaxCharge.getValue() != 0
                        && nonAlcoholicTaxCharge.getValue() != 0) {
                    BigDecimal alcoholicTaxableDerived = alcoholicTaxUnformatted
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(alcoholicTaxCharge.getValue()), 10, configuredDivideRoundingMode());
                    alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxableDerived, currency);
                    nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);
                }

                log.debug("TAKEAWAY - Tax calculated (reconciled): alcBase={}, alcRate={}, alcType={}, alcTaxUnf={}, alcTax={}, nonAlcBase={}, nonAlcRate={}, nonAlcType={}, nonAlcTaxUnf={}, nonAlcTax={}, totalTax={}",
                    alcoholicTaxBase,
                    alcoholicTaxCharge != null ? alcoholicTaxCharge.getValue() : null,
                    alcoholicTaxCharge != null ? alcoholicTaxCharge.getType() : null,
                    alcoholicTaxUnformatted,
                    alcoholicTaxAmount,
                    nonAlcoholicTaxBase,
                    nonAlcoholicTaxCharge != null ? nonAlcoholicTaxCharge.getValue() : null,
                    nonAlcoholicTaxCharge != null ? nonAlcoholicTaxCharge.getType() : null,
                    nonAlcoholicTaxUnformatted,
                    nonAlcoholicTaxAmount,
                    alcoholicTaxAmount.add(nonAlcoholicTaxAmount));
            } else {
                log.warn("Tax setup not configured for TAKEAWAY order type");
            }
            
            taxAmount = alcoholicTaxAmount.add(nonAlcoholicTaxAmount);
            log.info("TAKEAWAY Tax Calculation - Alcoholic: {}, Non-alcoholic: {}, Total: {}", 
                alcoholicTaxAmount, nonAlcoholicTaxAmount, taxAmount);
        }
        
        // Calculate total before additional discount
        BigDecimal totalBeforeAdditionalDiscount = CurrencyFormatter.formatAmount(
            subtotalAfterDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount), 
            currency);
        
        // ==================== STEP 5: APPLY ADDITIONAL DISCOUNT ====================
        BigDecimal additionalDiscountSavings = BigDecimal.ZERO;
        if (additionalDiscountValue != null && additionalDiscountType != null) {
            if (additionalDiscountType == DiscountType.PERCENT) {
                // Calculate percentage discount on total before additional discount
                additionalDiscountSavings = CurrencyFormatter.formatAmount(
                    totalBeforeAdditionalDiscount.multiply(additionalDiscountValue)
                        .divide(BigDecimal.valueOf(100), 10, configuredDivideRoundingMode()), 
                    currency);
            } else if (additionalDiscountType == DiscountType.FLAT) {
                // Apply flat discount
                additionalDiscountSavings = CurrencyFormatter.formatAmount(additionalDiscountValue, currency);
            }
        }
        
        // ==================== STEP 6: CALCULATE FINAL TOTAL ====================
        BigDecimal totalAmount = CurrencyFormatter.formatAmount(
            totalBeforeAdditionalDiscount.subtract(additionalDiscountSavings), 
            currency);
        
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        
        log.debug("COMPLETE ORDER CALCULATION - SubTotal: {}, SubtotalAfterDiscount: {}, Tax: {}, ServiceCharge: {}, PackingCharge: {}, AdditionalDiscount: {}, Total: {}", 
            subTotal, subtotalAfterDiscount, taxAmount, serviceChargeAmount, packingChargeAmount, additionalDiscountSavings, totalAmount);
        
        return new OrderCalculationResult(
            subTotal, subtotalAfterDiscount, taxAmount, alcoholicTaxAmount, nonAlcoholicTaxAmount,
            alcoholicTaxableAmount, nonAlcoholicTaxableAmount,
            serviceChargeAmount, packingChargeAmount,
            additionalDiscountSavings, totalAmount, orderDiscountSavings, bxgyResult);
    }

    /**
     * Recalculates totals (tax/charges/additional discount) when the subtotal is already known.
     * <p>
     * This method is used when item-level detail is not available; tax is computed using an average of configured
     * alcoholic and non-alcoholic rates. Order-level discount and additional discount are applied using the same rules
     * as {@link #calculateCompleteOrderTotals(List, List, UUID, UUID, PriceOverrideHelper.ActiveOverrideIndex, Discount, BigDecimal, DiscountType, OrderType, Locale)}.
     * </p>
     *
     * @param subTotal                subtotal to start from (typically after item/category/BXGY processing)
     * @param orderDiscount           optional order-level discount
     * @param additionalDiscountValue optional additional discount value applied after charges/taxes
     * @param additionalDiscountType  additional discount type
     * @param orderType               dine-in vs takeaway charge rules
     * @param userLocale              locale for localized messages
     * @param bxgyResult              optional BXGY calculation details (a placeholder is created when absent)
     * @return recalculated totals derived from the provided subtotal
     */
    @Override
    public OrderCalculationResult recalculateTotalsFromSubtotal(
            BigDecimal subTotal,
            Discount orderDiscount,
            BigDecimal additionalDiscountValue,
            DiscountType additionalDiscountType,
            OrderType orderType,
            Locale userLocale,
            BxgyCalculationResult bxgyResult) {
        
        // Create a dummy BXGY result if not provided (for consistency)
        if (bxgyResult == null) {
            bxgyResult = new BxgyCalculationResult(
                subTotal, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new ArrayList<>());
        }
        
        // Apply order-level discount
        OrderDiscountResult orderDiscountResult = applyOrderLevelDiscount(orderDiscount, subTotal, userLocale);
        BigDecimal subtotalAfterDiscount = orderDiscountResult.getFinalSubTotal();
        BigDecimal orderDiscountSavings = orderDiscountResult.getDiscountSavings();
        
        // Calculate tax and service charge
        // Note: This method doesn't have item information, so we use average tax rate
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        String currency = chainConfig.getCurrency();
        
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        BigDecimal packingChargeAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxAmount = null;
        BigDecimal nonAlcoholicTaxAmount = null;
        BigDecimal alcoholicTaxableAmount = null;
        BigDecimal nonAlcoholicTaxableAmount = null;
        
        if (orderType == OrderType.DINE_IN) {
            // Dine In: Service Charge on Subtotal, Tax on (Subtotal + Service Charge)
            RestaurantChainConfigProperties.ServiceChargesForDineIn dineInServiceCharge = 
                chainConfig.getServiceChargesForDineIn();
            // Calculate service charge WITHOUT formatting first (for accurate tax base calculation)
            BigDecimal unformattedServiceChargeAmount = BigDecimal.ZERO;
            if (dineInServiceCharge != null) {
                unformattedServiceChargeAmount = calculateChargeAmountUnformatted(
                    subtotalAfterDiscount,
                    BigDecimal.valueOf(dineInServiceCharge.getValue()),
                    dineInServiceCharge.getType());
                // Format service charge for storage/display
                serviceChargeAmount = CurrencyFormatter.formatAmount(unformattedServiceChargeAmount, currency);
            }
            
            // Use UNFORMATTED service charge for accurate tax base calculation
            BigDecimal taxBase = subtotalAfterDiscount.add(unformattedServiceChargeAmount);
            RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge = 
                chainConfig.getTaxSetup().getDineIn().getAlcoholic();
            RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge = 
                chainConfig.getTaxSetup().getDineIn().getNonAlcoholic();
            // Use average of alcoholic and non-alcoholic rates when item info is not available
            BigDecimal avgTaxValue = BigDecimal.valueOf(alcoholicTaxCharge.getValue())
                .add(BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()))
                .divide(BigDecimal.valueOf(2), 10, configuredDivideRoundingMode());
            // Use PERCENT type as default when averaging
            ChargeType avgTaxType = alcoholicTaxCharge.getType() == ChargeType.FLAT && 
                                   nonAlcoholicTaxCharge.getType() == ChargeType.FLAT ? ChargeType.FLAT : ChargeType.PERCENT;
            
            taxAmount = calculateChargeAmount(taxBase, avgTaxValue, avgTaxType, currency);
        } else {
            // Takeaway: Packaging Charge on Subtotal, Tax on (Subtotal + Packaging Charge)
            RestaurantChainConfigProperties.PackingChargesForTakeaway packingCharges = 
                chainConfig.getPackingChargesForTakeaway();
            // Calculate packing charge WITHOUT formatting first (for accurate tax base calculation)
            BigDecimal unformattedPackingChargeAmount = calculateChargeAmountUnformatted(
                subtotalAfterDiscount,
                BigDecimal.valueOf(packingCharges.getValue()),
                packingCharges.getType());
            // Format packing charge for storage/display
            packingChargeAmount = CurrencyFormatter.formatAmount(unformattedPackingChargeAmount, currency);
            
            // Use UNFORMATTED packing charge for accurate tax base calculation
            BigDecimal taxBase = subtotalAfterDiscount.add(unformattedPackingChargeAmount);
            RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge = 
                chainConfig.getTaxSetup().getTakeAway().getAlcoholic();
            RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge = 
                chainConfig.getTaxSetup().getTakeAway().getNonAlcoholic();
            BigDecimal avgTaxValue = BigDecimal.valueOf(alcoholicTaxCharge.getValue())
                .add(BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()))
                .divide(BigDecimal.valueOf(2), 10, configuredDivideRoundingMode());
            // Use PERCENT type as default when averaging
            ChargeType avgTaxType = alcoholicTaxCharge.getType() == ChargeType.FLAT && 
                                   nonAlcoholicTaxCharge.getType() == ChargeType.FLAT ? ChargeType.FLAT : ChargeType.PERCENT;
            
            taxAmount = calculateChargeAmount(taxBase, avgTaxValue, avgTaxType, currency);
        }
        
        BigDecimal totalBeforeAdditionalDiscount = CurrencyFormatter.formatAmount(
            subtotalAfterDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount), 
            currency);
        
        // Apply additional discount
        BigDecimal additionalDiscountSavings = BigDecimal.ZERO;
        if (additionalDiscountValue != null && additionalDiscountType != null) {
            if (additionalDiscountType == DiscountType.PERCENT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(
                    totalBeforeAdditionalDiscount.multiply(additionalDiscountValue)
                        .divide(BigDecimal.valueOf(100), 10, configuredDivideRoundingMode()), 
                    currency);
            } else if (additionalDiscountType == DiscountType.FLAT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(additionalDiscountValue, currency);
            }
        }
        
        BigDecimal totalAmount = CurrencyFormatter.formatAmount(
            totalBeforeAdditionalDiscount.subtract(additionalDiscountSavings), 
            currency);
        
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        
        return new OrderCalculationResult(
            subTotal, subtotalAfterDiscount, taxAmount, alcoholicTaxAmount, nonAlcoholicTaxAmount,
            alcoholicTaxableAmount, nonAlcoholicTaxableAmount,
            serviceChargeAmount, packingChargeAmount,
            additionalDiscountSavings, totalAmount, orderDiscountSavings, bxgyResult);
    }

    /**
     * Applies an order-level discount to a subtotal.
     * <p>
     * Supports PERCENT and FLAT discounts with currency-aware formatting/rounding. BXGY discounts are not applied at
     * order level and are expected to be handled earlier at item level.
     * </p>
     *
     * @param orderDiscount discount to apply (nullable)
     * @param subTotal      subtotal before applying the discount
     * @param userLocale    locale for any localized error messages/logging context
     * @return discount result containing final subtotal and savings
     */
    @Override
    public OrderDiscountResult applyOrderLevelDiscount(Discount orderDiscount, BigDecimal subTotal, Locale userLocale) {
        if (orderDiscount == null) {
            return new OrderDiscountResult(subTotal, BigDecimal.ZERO, null);
        }
        
        BigDecimal discountSavings = BigDecimal.ZERO;
        BigDecimal finalSubTotal = subTotal;
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        // Apply discount based on type
        if (orderDiscount.getDiscountType() == DiscountType.PERCENT) {
            // Calculate percentage discount with high precision, then format
            discountSavings = subTotal.multiply(orderDiscount.getValue())
                    .divide(BigDecimal.valueOf(100), 10, configuredDivideRoundingMode());
            
            // Apply maximum discount limit if specified
            if (orderDiscount.getMaxDiscountValue() != null) {
                discountSavings = discountSavings.min(orderDiscount.getMaxDiscountValue());
            }
            
            // Format discount savings according to currency
            discountSavings = CurrencyFormatter.formatAmount(discountSavings, currency);
            
            finalSubTotal = subTotal.subtract(discountSavings);
            // Format final subtotal according to currency
            finalSubTotal = CurrencyFormatter.formatAmount(finalSubTotal, currency);
            
            // Format calculated discount for logging consistency
            BigDecimal calculatedDiscountForLog = CurrencyFormatter.formatAmount(
                subTotal.multiply(orderDiscount.getValue()).divide(BigDecimal.valueOf(100), 10, configuredDivideRoundingMode()),
                currency);
            log.debug("Order discount calculation: Subtotal={}, DiscountValue={}, CalculatedDiscount={}, MaxDiscount={}, FinalDiscount={}, FinalSubTotal={}", 
                subTotal, orderDiscount.getValue(), calculatedDiscountForLog, 
                orderDiscount.getMaxDiscountValue(), discountSavings, finalSubTotal);
                
        } else if (orderDiscount.getDiscountType() == DiscountType.FLAT) {
            // Flat amount discount - format according to currency
            discountSavings = CurrencyFormatter.formatAmount(orderDiscount.getValue(), currency);
            finalSubTotal = subTotal.subtract(discountSavings);
            // Format final subtotal according to currency
            finalSubTotal = CurrencyFormatter.formatAmount(finalSubTotal, currency);
            
        } else if (orderDiscount.getDiscountType() == DiscountType.BXGY) {
            // BXGY discounts are handled at item level, not order level
            log.warn("BXGY discount type should not be applied at order level: {}", orderDiscount.getId());
            return new OrderDiscountResult(subTotal, BigDecimal.ZERO, orderDiscount);
        }
        
        // Ensure final subtotal doesn't go below zero
        if (finalSubTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalSubTotal = BigDecimal.ZERO;
            discountSavings = CurrencyFormatter.formatAmount(subTotal, currency); // Maximum possible discount - format according to currency
        }
        
        log.debug("Applied order-level discount {}: Original subtotal: {}, Discount: {}, Final subtotal: {}", 
                orderDiscount.getId(), subTotal, discountSavings, finalSubTotal);
        
        return new OrderDiscountResult(finalSubTotal, discountSavings, orderDiscount);
    }

    /**
     * Finds the {@link CategoryItemMapping} for an item within a menu by scanning menu-category mappings.
     *
     * @param menuId menu identifier
     * @param itemId item identifier
     * @return matching mapping, or {@code null} when the item is not mapped under the menu
     */
    @Override
    public CategoryItemMapping getCategoryItemMapping(UUID menuId, UUID itemId) {
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        
        for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappings) {
            CategoryItemMapping mapping = categoryItemMappingRepository
                    .findByMenuCategoryMapping_IdAndItem_Id(menuCategoryMapping.getId(), itemId);
            if (mapping != null) {
                return mapping;
            }
        }
        return null;
    }

    /**
     * Computes a discounted total price for a given base unit price and quantity.
     * <p>
     * This is a simple price helper used by discount logic. For PERCENT discounts, the discount is computed per unit
     * (rounded) and then multiplied by quantity; for FLAT discounts, the unit price is clamped at zero before scaling.
     * Order-level caps/uses are intentionally not applied here (item/category level logic).
     * </p>
     *
     * @param basePrice base unit price
     * @param discount  discount definition (PERCENT or FLAT)
     * @param quantity  quantity (must be non-null)
     * @return discounted total price for the quantity
     */
    @Override
    public BigDecimal calculateDiscountedPrice(BigDecimal basePrice, Discount discount, Integer quantity) {
        BigDecimal discountedPrice;
        if (discount.getDiscountType() == DiscountType.PERCENT) {
            BigDecimal discountAmount = basePrice.multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 2, configuredDivideRoundingMode());
            // Note: maxDiscountValue check removed for item/category-level discounts
            BigDecimal discountedUnitPrice = basePrice.subtract(discountAmount);
            discountedPrice = discountedUnitPrice.multiply(BigDecimal.valueOf(quantity));
        } else {
            BigDecimal discountedUnitPrice = basePrice.subtract(discount.getValue()).max(BigDecimal.ZERO);
            discountedPrice = discountedUnitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        // Note: maxUses check removed for item/category-level discounts
        
        return discountedPrice;
    }

    /**
     * Chooses the better discount result between item-level and category-level candidates.
     * <p>
     * When both candidates are present, it compares effective discount percentage relative to the (effective) base
     * unit price and selects the higher percentage; ties prefer item-level.
     * </p>
     *
     * @param itemDiscount     item-level discount candidate (may represent "no discount")
     * @param categoryDiscount category-level discount candidate (may represent "no discount")
     * @param basePrice        base unit price used for percentage comparison (after overrides)
     * @param quantity         quantity for the pricing context
     * @return the chosen discount calculation result
     */
    @Override
    public DiscountCalculationResult chooseBestDiscount(
            DiscountCalculationResult itemDiscount,
            DiscountCalculationResult categoryDiscount,
            BigDecimal basePrice,
            Integer quantity) {
        BigDecimal totalOriginalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
        
        // If no discounts are applied, return the item discount (which will have no discount)
        if (itemDiscount.getAppliedDiscount() == null && categoryDiscount.getAppliedDiscount() == null) {
            return itemDiscount;
        }
        
        // If only one discount is applied, return that one
        if (itemDiscount.getAppliedDiscount() == null) {
            return categoryDiscount;
        }
        if (categoryDiscount.getAppliedDiscount() == null) {
            return itemDiscount;
        }
        
        // Both discounts are applied - compare based on discount percentage
        double basePriceDouble = basePrice.doubleValue();
        double itemDiscountPercentage = calculateDiscountPercentage(itemDiscount.getAppliedDiscount(), basePriceDouble);
        double categoryDiscountPercentage = calculateDiscountPercentage(categoryDiscount.getAppliedDiscount(), basePriceDouble);
        
        log.debug("DISCOUNT COMPARISON - Item Discount: {}% ({}), Category Discount: {}% ({})",
            itemDiscountPercentage, itemDiscount.getAppliedDiscount().getId(),
            categoryDiscountPercentage, categoryDiscount.getAppliedDiscount().getId());
        
        // Select the discount with the highest percentage
        if (itemDiscountPercentage >= categoryDiscountPercentage) {
            log.debug("Selected item-level discount with {}%", itemDiscountPercentage);
            return itemDiscount;
        } else {
            log.debug("Selected category-level discount with {}%", categoryDiscountPercentage);
            return categoryDiscount;
        }
    }

    /**
     * Calculate discount amount in currency units
     * Note: maxDiscountValue is NOT applied for item/category-level discounts
     */
    private double calculateDiscountAmount(Discount discount, double basePrice) {
        if (discount.getDiscountType() == DiscountType.PERCENT) {
            double percentage = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
            // Note: maxDiscountValue check removed for item/category-level discounts
            return (basePrice * percentage) / 100.0;
        } else if (discount.getDiscountType() == DiscountType.FLAT) {
            double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
            return Math.min(flatAmount, basePrice); // Can't discount more than the base price
        }
        return 0.0;
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Calculate discount percentage for comparison purposes
     * For PERCENT discounts: returns the percentage value directly
     * For FLAT discounts: converts to percentage based on base price
     */
    private double calculateDiscountPercentage(Discount discount, double basePrice) {
        if (discount.getDiscountType() == DiscountType.PERCENT) {
            return discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        } else if (discount.getDiscountType() == DiscountType.FLAT) {
            double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
            return basePrice > 0 ? (flatAmount / basePrice) * 100.0 : 0.0;
        }
        return 0.0;
    }

    /**
     * Check if discount is valid for menu and time
     * Delegates to OrderValidationService which handles restaurant-level validation
     */
    private boolean isDiscountValidForMenuAndTime(UUID menuId, UUID discountId, UUID restaurantId) {
        return orderValidationService.isDiscountValidForMenuAndTime(menuId, discountId, restaurantId);
    }


    @Override
    public DiscountCalculationResult calculateItemPrice(UUID menuId, UUID itemId, Integer quantity) {
        // Simple wrapper - calculate without price override
        return calculateItemPriceWithOverride(menuId, itemId, quantity, null, null);
    }

    /**
     * Calculates item price and best applicable discount for an item id, optionally applying a price override.
     * <p>
     * Validates the menu and item exist, resolves the {@link Item}, then delegates to the overload accepting an
     * {@link Item} to perform discount selection and BXGY-priority checks.
     * </p>
     *
     * @param menuId              menu identifier
     * @param itemId              item identifier
     * @param quantity            quantity
     * @param restaurantId        restaurant context (used for discount validity and price overrides)
     * @param activeOverrideIndex active overrides index (optional)
     * @return discount calculation result containing original/final totals and the applied discount (if any)
     * @throws ResponseStatusException when menu or item cannot be found
     */
    @Override
    public DiscountCalculationResult calculateItemPriceWithOverride(
            UUID menuId, 
            UUID itemId, 
            Integer quantity,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        
        // Validate menu exists (but don't store it)
        menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", LocaleContextHolder.getLocale())));
        
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ITEM_NOT_FOUND, LocaleContextHolder.getLocale())));
        
        // Delegate to overloaded method that accepts Item
        return calculateItemPriceWithOverride(menuId, item, quantity, restaurantId, activeOverrideIndex);
    }

    /**
     * Calculates item price and best applicable discount for a provided {@link Item}, optionally applying a price override.
     * <p>
     * BXGY is treated as higher priority than item/category discounts: when a BXGY discount is configured/active for
     * this item, the method returns the (possibly overridden) base price total with no applied discount metadata so
     * BXGY can be computed later across buy/get sets.
     * </p>
     *
     * @param menuId              menu identifier
     * @param item                item entity to price
     * @param quantity            quantity
     * @param restaurantId        restaurant context (used for discount validity and price overrides)
     * @param activeOverrideIndex active overrides index (optional)
     * @return discount calculation result for the item
     * @throws ResponseStatusException when menu is missing or {@code item} is null
     */
    @Override
    public DiscountCalculationResult calculateItemPriceWithOverride(
            UUID menuId, 
            Item item, 
            Integer quantity,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        
        // Validate menu exists (but don't store it)
        menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", LocaleContextHolder.getLocale())));
        
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_ITEM_NOT_FOUND, LocaleContextHolder.getLocale()));
        }
        
        Double basePrice = item.getBasePrice();
        BigDecimal basePriceBigDecimal = BigDecimal.valueOf(basePrice);

        // Find MenuCategoryMappings for this menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        
        if (menuCategoryMappings.isEmpty()) {
            BigDecimal totalPrice = basePriceBigDecimal.multiply(BigDecimal.valueOf(quantity));
            return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
        }

        // Find CategoryItemMapping for this item
        UUID itemId = item.getId();
        Optional<CategoryItemMapping> categoryItemMappingOpt = Optional.empty();
        for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappings) {
            CategoryItemMapping mapping = categoryItemMappingRepository
                    .findByMenuCategoryMapping_IdAndItem_Id(menuCategoryMapping.getId(), itemId);
            if (mapping != null) {
                categoryItemMappingOpt = Optional.of(mapping);
                break;
            }
        }
        
        if (categoryItemMappingOpt.isEmpty()) {
            BigDecimal totalPrice = basePriceBigDecimal.multiply(BigDecimal.valueOf(quantity));
            return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
        }
        
        CategoryItemMapping categoryItemMapping = categoryItemMappingOpt.get();
        
        // ==================== STEP 1.5: CHECK FOR BXGY DISCOUNT FIRST (PRIORITY) ====================
        // Check if BXGY discount exists for this item via database mappings (DiscountBxgyItem)
        // BXGY discount takes priority over item/category discounts - if BXGY exists, skip item/category discount calculation
        boolean hasBxgyDiscount = checkBxgyDiscountExistsForItem(menuId, itemId, categoryItemMapping, restaurantId);
        
        log.debug("BXGY check result for item {}: {}", itemId, hasBxgyDiscount);
        
        // Get all MenuCategoryMappings for this item (item can be in multiple categories)
        // This is needed for both price override and BXGY check
        List<CategoryItemMapping> allItemMappings = categoryItemMappingRepository
                .findByItem_Id(itemId)
                .stream()
                .filter(mapping -> mapping.getMenuCategoryMapping().getMenu().getId().equals(menuId))
                .collect(Collectors.toList());
        
        List<MenuCategoryMapping> itemMcms = allItemMappings.stream()
                .map(CategoryItemMapping::getMenuCategoryMapping)
                .distinct()
                .collect(Collectors.toList());
        
        if (hasBxgyDiscount) {
            // BXGY discount exists - calculate BASE PRICE ONLY (no item/category discounts)
            // Apply price override if available
            Double effectiveBasePrice = basePrice;
            if (restaurantId != null && activeOverrideIndex != null && !itemMcms.isEmpty()) {
                effectiveBasePrice = priceOverrideHelper.resolveEffectiveBasePrice(
                        basePrice, 
                        menuId, 
                        itemMcms, 
                        activeOverrideIndex);
            }
            
            BigDecimal totalPrice = BigDecimal.valueOf(effectiveBasePrice).multiply(BigDecimal.valueOf(quantity));
            // Return result with originalPrice = finalPrice (no discounts applied)
            log.debug("BXGY Discount Found - Calculating BASE PRICE ONLY (no item/category discounts) for item: {}, BasePrice: {}, TotalPrice: {}", 
                itemId, effectiveBasePrice, totalPrice);
            return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
        }
        
        // ==================== STEP 2: APPLY PRICE OVERRIDE ====================
        
        // Apply price override using PriceOverrideHelper
        Double effectiveBasePrice = basePrice;
        if (restaurantId != null && activeOverrideIndex != null && !itemMcms.isEmpty()) {
            effectiveBasePrice = priceOverrideHelper.resolveEffectiveBasePrice(
                    basePrice, 
                    menuId, 
                    itemMcms, 
                    activeOverrideIndex);
        }
        
        BigDecimal effectiveBasePriceBigDecimal = BigDecimal.valueOf(effectiveBasePrice);
        
        DiscountCalculationResult itemDiscountResult = calculateItemLevelDiscount(
                menuId, 
                categoryItemMapping, 
                effectiveBasePriceBigDecimal,  // Use overridden price
                quantity,
                restaurantId);
        
        // Check all categories for this item, not just one category
        DiscountCalculationResult categoryDiscountResult = calculateCategoryLevelDiscountForItem(
                menuId, 
                itemId,
                effectiveBasePriceBigDecimal,  // Use overridden price
                quantity,
                restaurantId);
        
        log.debug("    - Item-Level Discount: {} -> {} (Savings: {})",
            itemDiscountResult.getOriginalPrice(), 
            itemDiscountResult.getFinalPrice(),
            itemDiscountResult.getOriginalPrice().subtract(itemDiscountResult.getFinalPrice()));
        log.debug("    - Category-Level Discount: {} -> {} (Savings: {})",
            categoryDiscountResult.getOriginalPrice(), 
            categoryDiscountResult.getFinalPrice(),
            categoryDiscountResult.getOriginalPrice().subtract(categoryDiscountResult.getFinalPrice()));

        return chooseBestDiscount(
                itemDiscountResult, 
                categoryDiscountResult, 
                effectiveBasePriceBigDecimal,  // Use overridden price for comparison
                quantity);
    }

    @Override
    public DiscountCalculationResult calculateItemLevelDiscount(UUID menuId, CategoryItemMapping categoryItemMapping,
                                                                BigDecimal basePrice, Integer quantity) {
        return calculateItemLevelDiscount(menuId, categoryItemMapping, basePrice, quantity, null);
    }

    /**
     * Calculates the best item-level discount for an item across all of its mappings within a menu.
     * <p>
     * Scans item discount mappings, filters out BXGY (handled separately), validates discounts by menu/time/restaurant,
     * and applies the maximum effective discount percentage.
     * </p>
     *
     * @param menuId             menu identifier
     * @param categoryItemMapping a representative mapping for the item
     * @param basePrice          base unit price used to compute discount values
     * @param quantity           quantity
     * @param restaurantId       restaurant context for discount validity (optional)
     * @return discount result (original vs discounted totals) or a no-discount result when none apply
     */
    @Override
    public DiscountCalculationResult calculateItemLevelDiscount(UUID menuId, CategoryItemMapping categoryItemMapping,
                                                                BigDecimal basePrice, Integer quantity, UUID restaurantId) {
        // Get all item mappings for this item in the menu to check all possible discounts
        List<CategoryItemMapping> allItemMappings = categoryItemMappingRepository
                .findByItem_Id(categoryItemMapping.getItem().getId())
                .stream()
                .filter(mapping -> mapping.getMenuCategoryMapping().getMenu().getId().equals(menuId))
                .collect(Collectors.toList());

        if (allItemMappings.isEmpty()) {
            BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
            return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
        }

        // Check all item-level discounts across all mappings and find the best one
        double maxDiscountPercentage = 0.0;
        BigDecimal maxDiscountAmount = BigDecimal.ZERO;
        Discount bestDiscount = null;
        AppliedTo bestAppliedTo = null;

        for (CategoryItemMapping itemMapping : allItemMappings) {
            List<ItemDiscountMapping> itemDiscountMappings = itemDiscountMappingRepository
                    .findByCategoryItemMapping(itemMapping);

            for (ItemDiscountMapping itemDiscountMapping : itemDiscountMappings) {
                Discount discount = itemDiscountMapping.getDiscount();
                
                // Skip BXGY discounts - they are handled separately
                if (discount.getDiscountType() == DiscountType.BXGY) {
                    continue;
                }

                log.debug("ITEM DISCOUNT CHECK - Item: {}, Discount: {}, Value: {}, Type: {}, Valid: {}",
                    categoryItemMapping.getItem().getId(), discount.getId(), discount.getValue(),
                    discount.getDiscountType(), isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId));

                if (isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                    // Calculate discount percentage to find the best discount
                    double basePriceDouble = basePrice.doubleValue();
                    double discountPercentage = calculateDiscountPercentage(discount, basePriceDouble);
                    
                    if (discountPercentage > maxDiscountPercentage) {
                        maxDiscountPercentage = discountPercentage;
                        maxDiscountAmount = BigDecimal.valueOf(calculateDiscountAmount(discount, basePriceDouble));
                        bestDiscount = discount;
                        bestAppliedTo = AppliedTo.ITEM;
                    }
                }
            }
        }

        // Apply the best discount found
        if (bestDiscount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalOriginalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal totalDiscountAmount = maxDiscountAmount.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discountedPrice = totalOriginalPrice.subtract(totalDiscountAmount);
            
            // Ensure discounted price is not negative
            if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
                discountedPrice = BigDecimal.ZERO;
            }
            
            log.debug("ITEM DISCOUNT APPLIED - Item: {}, Original: {}, Discounted: {}, Discount: {}, Percentage: {}%",
                categoryItemMapping.getItem().getId(), totalOriginalPrice,
                discountedPrice, bestDiscount.getValue(), maxDiscountPercentage);
            
            return new DiscountCalculationResult(
                    totalOriginalPrice,
                    discountedPrice,
                    bestDiscount,
                    bestAppliedTo
            );
        }

        BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
        return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
    }

    @Override
    public DiscountCalculationResult calculateCategoryLevelDiscount(UUID menuId, MenuCategoryMapping menuCategoryMapping,
                                                                   BigDecimal basePrice, Integer quantity) {
        return calculateCategoryLevelDiscount(menuId, menuCategoryMapping, basePrice, quantity, null);
    }

    /**
     * Calculates the best category-level discount for a category (and its parent, if present).
     * <p>
     * Filters out BXGY discounts, validates discounts by menu/time/restaurant, and applies the maximum effective
     * discount percentage across direct and parent category mappings.
     * </p>
     *
     * @param menuId             menu identifier
     * @param menuCategoryMapping category mapping to evaluate
     * @param basePrice          base unit price used to compute discount values
     * @param quantity           quantity
     * @param restaurantId       restaurant context for discount validity (optional)
     * @return discount result or a no-discount result when none apply
     */
    @Override
    public DiscountCalculationResult calculateCategoryLevelDiscount(UUID menuId, MenuCategoryMapping menuCategoryMapping,
                                                                   BigDecimal basePrice, Integer quantity, UUID restaurantId) {
        log.debug("Checking category-level discount for category: {} in menu: {}", menuCategoryMapping.getCategory().getId(), menuId);
        
        // Check category-level discounts - including parent categories
        double maxDiscountPercentage = 0.0;
        BigDecimal maxDiscountAmount = BigDecimal.ZERO;
        Discount bestDiscount = null;
        AppliedTo bestAppliedTo = null;

        // Check discounts on the direct category
        List<CategoryDiscountMapping> categoryDiscountMappings = categoryDiscountMappingRepository
                .findByMenuCategoryMapping(menuCategoryMapping);

        log.debug("Found {} category discount mappings for category: {}", categoryDiscountMappings.size(), menuCategoryMapping.getCategory().getId());

        for (CategoryDiscountMapping categoryDiscountMapping : categoryDiscountMappings) {
            Discount discount = categoryDiscountMapping.getDiscount();
            
            // Skip BXGY discounts - they are handled separately
            if (discount.getDiscountType() == DiscountType.BXGY) {
                continue;
            }

            log.debug("CATEGORY DISCOUNT CHECK - Category: {}, Discount: {}, Value: {}, Type: {}, Valid: {}", 
                menuCategoryMapping.getCategory().getId(), discount.getId(), discount.getValue(), 
                discount.getDiscountType(), orderValidationService.isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId));

            if (isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                // Calculate discount percentage to find the best discount
                double basePriceDouble = basePrice.doubleValue();
                double discountPercentage = calculateDiscountPercentage(discount, basePriceDouble);
                
                if (discountPercentage > maxDiscountPercentage) {
                    maxDiscountPercentage = discountPercentage;
                    maxDiscountAmount = BigDecimal.valueOf(calculateDiscountAmount(discount, basePriceDouble));
                    bestDiscount = discount;
                    bestAppliedTo = AppliedTo.CATEGORY;
                }
            } else {
                log.debug("CATEGORY DISCOUNT REJECTED - Category: {}, Discount: {}, Reason: Validation failed", 
                    menuCategoryMapping.getCategory().getId(), discount.getId());
            }
        }

        // Check parent category discounts if this category has a parent
        if (menuCategoryMapping.getCategory() != null && menuCategoryMapping.getCategory().getParentCategory() != null) {
            UUID parentCategoryId = menuCategoryMapping.getCategory().getParentCategory().getId();
            Optional<MenuCategoryMapping> parentMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, parentCategoryId);
            
            if (parentMcm.isPresent()) {
                List<CategoryDiscountMapping> parentCategoryDiscounts = categoryDiscountMappingRepository
                        .findByMenuCategoryMapping(parentMcm.get());
                
                for (CategoryDiscountMapping cdm : parentCategoryDiscounts) {
                    Discount discount = cdm.getDiscount();
                    
                    // Skip BXGY discounts
                    if (discount.getDiscountType() == DiscountType.BXGY) {
                        continue;
                    }
                    
                    if (isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                        double basePriceDouble = basePrice.doubleValue();
                        double discountPercentage = calculateDiscountPercentage(discount, basePriceDouble);
                        
                        if (discountPercentage > maxDiscountPercentage) {
                            maxDiscountPercentage = discountPercentage;
                            maxDiscountAmount = BigDecimal.valueOf(calculateDiscountAmount(discount, basePriceDouble));
                            bestDiscount = discount;
                            bestAppliedTo = AppliedTo.CATEGORY;
                        }
                    }
                }
            }
        }

        // Apply the best discount found
        if (bestDiscount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalOriginalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal totalDiscountAmount = maxDiscountAmount.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discountedPrice = totalOriginalPrice.subtract(totalDiscountAmount);
            
            // Ensure discounted price is not negative
            if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
                discountedPrice = BigDecimal.ZERO;
            }
            
            log.debug("CATEGORY DISCOUNT APPLIED - Category: {}, Original: {}, Discounted: {}, Discount: {}, Percentage: {}%", 
                menuCategoryMapping.getCategory().getId(), totalOriginalPrice, 
                discountedPrice, bestDiscount.getValue(), maxDiscountPercentage);
            
            return new DiscountCalculationResult(
                    totalOriginalPrice,
                    discountedPrice,
                    bestDiscount,
                    bestAppliedTo
            );
        }

        log.debug("No valid category discounts found for category: {}", menuCategoryMapping.getCategory().getId());
        BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
        return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
    }

    @Override
    public DiscountCalculationResult calculateCategoryLevelDiscountForItem(UUID menuId, UUID itemId, 
                                                                           BigDecimal basePrice, Integer quantity) {
        return calculateCategoryLevelDiscountForItem(menuId, itemId, basePrice, quantity, null);
    }

    /**
     * Calculates the best category-level discount applicable to a specific item across all its menu categories.
     * <p>
     * Evaluates category and parent-category discounts for each mapping the item belongs to, filters out BXGY,
     * validates by menu/time/restaurant, and applies the maximum effective discount percentage.
     * </p>
     *
     * @param menuId       menu identifier
     * @param itemId       item identifier
     * @param basePrice    base unit price used to compute discount values
     * @param quantity     quantity
     * @param restaurantId restaurant context for discount validity (optional)
     * @return discount result or a no-discount result when none apply
     */
    @Override
    public DiscountCalculationResult calculateCategoryLevelDiscountForItem(UUID menuId, UUID itemId, 
                                                                           BigDecimal basePrice, Integer quantity, UUID restaurantId) {
        log.debug("Checking category-level discounts for item: {} in menu: {}", itemId, menuId);
        
        // Get all item mappings for this item in the menu to check all category discounts
        List<CategoryItemMapping> allItemMappings = categoryItemMappingRepository
                .findByItem_Id(itemId)
                .stream()
                .filter(mapping -> mapping.getMenuCategoryMapping().getMenu().getId().equals(menuId))
                .collect(Collectors.toList());

        if (allItemMappings.isEmpty()) {
            BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
            return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
        }

        // Check category-level discounts across all categories - including parent categories
        double maxDiscountPercentage = 0.0;
        BigDecimal maxDiscountAmount = BigDecimal.ZERO;
        Discount bestDiscount = null;
        AppliedTo bestAppliedTo = null;
        Set<UUID> checkedCategoryIds = new HashSet<>(); // Avoid checking same category twice

        for (CategoryItemMapping itemMapping : allItemMappings) {
            MenuCategoryMapping mcm = itemMapping.getMenuCategoryMapping();
            UUID categoryId = mcm.getCategory().getId();
            
            // Skip if we've already checked this category
            if (checkedCategoryIds.contains(categoryId)) {
                continue;
            }
            checkedCategoryIds.add(categoryId);

            // Check discounts on the direct category
            List<CategoryDiscountMapping> categoryDiscountMappings = categoryDiscountMappingRepository
                    .findByMenuCategoryMapping(mcm);

            for (CategoryDiscountMapping categoryDiscountMapping : categoryDiscountMappings) {
                Discount discount = categoryDiscountMapping.getDiscount();
                
                // Skip BXGY discounts - they are handled separately
                if (discount.getDiscountType() == DiscountType.BXGY) {
                    continue;
                }

                if (isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                    double basePriceDouble = basePrice.doubleValue();
                    double discountPercentage = calculateDiscountPercentage(discount, basePriceDouble);
                    
                    if (discountPercentage > maxDiscountPercentage) {
                        maxDiscountPercentage = discountPercentage;
                        maxDiscountAmount = BigDecimal.valueOf(calculateDiscountAmount(discount, basePriceDouble));
                        bestDiscount = discount;
                        bestAppliedTo = AppliedTo.CATEGORY;
                    }
                }
            }

            // Check parent category discounts if this category has a parent
            if (mcm.getCategory() != null && mcm.getCategory().getParentCategory() != null) {
                UUID parentCategoryId = mcm.getCategory().getParentCategory().getId();
                
                // Skip if we've already checked this parent category
                if (!checkedCategoryIds.contains(parentCategoryId)) {
                    Optional<MenuCategoryMapping> parentMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, parentCategoryId);
                    
                    if (parentMcm.isPresent()) {
                        checkedCategoryIds.add(parentCategoryId);
                        List<CategoryDiscountMapping> parentCategoryDiscounts = categoryDiscountMappingRepository
                                .findByMenuCategoryMapping(parentMcm.get());
                        
                        for (CategoryDiscountMapping cdm : parentCategoryDiscounts) {
                            Discount discount = cdm.getDiscount();
                            
                            // Skip BXGY discounts
                            if (discount.getDiscountType() == DiscountType.BXGY) {
                                continue;
                            }
                            
                            if (isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                                double basePriceDouble = basePrice.doubleValue();
                                double discountPercentage = calculateDiscountPercentage(discount, basePriceDouble);
                                
                                if (discountPercentage > maxDiscountPercentage) {
                                    maxDiscountPercentage = discountPercentage;
                                    maxDiscountAmount = BigDecimal.valueOf(calculateDiscountAmount(discount, basePriceDouble));
                                    bestDiscount = discount;
                                    bestAppliedTo = AppliedTo.CATEGORY;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply the best discount found
        if (bestDiscount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalOriginalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal totalDiscountAmount = maxDiscountAmount.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discountedPrice = totalOriginalPrice.subtract(totalDiscountAmount);
            
            // Ensure discounted price is not negative
            if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
                discountedPrice = BigDecimal.ZERO;
            }
            
            log.debug("CATEGORY DISCOUNT APPLIED - Item: {}, Original: {}, Discounted: {}, Discount: {}, Percentage: {}%", 
                itemId, totalOriginalPrice, 
                discountedPrice, bestDiscount.getValue(), maxDiscountPercentage);
            
            return new DiscountCalculationResult(
                    totalOriginalPrice,
                    discountedPrice,
                    bestDiscount,
                    bestAppliedTo
            );
        }

        log.debug("No valid category discounts found for item: {}", itemId);
        BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(quantity));
        return new DiscountCalculationResult(totalPrice, totalPrice, null, null);
    }

    @Override
    public List<UUID> ensureBxgyDiscountIdsIncluded(OrderedItemRequest itemRequest, UUID menuId) {
        return ensureBxgyDiscountIdsIncluded(itemRequest, menuId, null);
    }

    /**
     * Ensures BXGY discount ids are present on the request when the request flags indicate buy/get participation.
     * <p>
     * If {@code isBuyItem} and/or {@code isGetItem} is true, this method:
     * </p>
     * <ul>
     *   <li>Retains existing BXGY discount ids already present on the request.</li>
     *   <li>Otherwise discovers BXGY discounts configured for the item (across all menu mappings) using batch queries.</li>
     *   <li>Validates discovered discounts by menu/time/restaurant before adding them.</li>
     * </ul>
     *
     * @param itemRequest  item request to normalize (nullable)
     * @param menuId       menu identifier
     * @param restaurantId restaurant context for discount validity (optional)
     * @return a mutable list of discount ids that includes applicable BXGY ids when required
     */
    private List<UUID> ensureBxgyDiscountIdsIncluded(OrderedItemRequest itemRequest, UUID menuId, UUID restaurantId) {
        if (itemRequest == null) {
            return new ArrayList<>();
        }
        
        List<UUID> discountIds = (itemRequest.getDiscountIds() != null) 
            ? new ArrayList<>(itemRequest.getDiscountIds()) 
            : new ArrayList<>();
        
        // If isBuyItem or isGetItem is true, ensure the corresponding BXGY discount ID is in the list
        if (Boolean.TRUE.equals(itemRequest.getIsBuyItem()) || Boolean.TRUE.equals(itemRequest.getIsGetItem())) {
            // Find BXGY discount IDs from the existing discountIds list
            Set<UUID> bxgyDiscountIds = new HashSet<>();
            for (UUID discountId : discountIds) {
                Discount discount = discountRepository.findById(discountId).orElse(null);
                if (discount != null && discount.getDiscountType() == DiscountType.BXGY) {
                    bxgyDiscountIds.add(discountId);
                }
            }
            
            // If no BXGY discount ID found in the list, try to find it from the item configuration
            // Use the same batch query approach as MenuServiceImpl for consistency
            if (bxgyDiscountIds.isEmpty()) {
                // Get ALL CategoryItemMappings for this item in the menu (not just one)
                List<CategoryItemMapping> allItemMappings = categoryItemMappingRepository
                        .findByItem_Id(itemRequest.getItemId())
                        .stream()
                        .filter(mapping -> mapping.getMenuCategoryMapping().getMenu().getId().equals(menuId))
                        .collect(Collectors.toList());
                
                if (!allItemMappings.isEmpty()) {
                    // Extract CategoryItemMapping IDs for batch query
                    List<UUID> categoryItemMappingIds = allItemMappings.stream()
                            .map(CategoryItemMapping::getId)
                            .collect(Collectors.toList());
                    
                    if (Boolean.TRUE.equals(itemRequest.getIsBuyItem())) {
                        // Query buy items by CategoryItemMapping IDs in batch
                        List<DiscountBxgyItem> buyItems = discountBxgyItemRepository
                                .findByBuyItemMappingIdsAndMenuId(categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
                        for (DiscountBxgyItem bxgyItem : buyItems) {
                            Discount discount = bxgyItem.getDiscount();
                            if (discount != null && 
                                orderValidationService.isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                                bxgyDiscountIds.add(discount.getId());
                            }
                        }
                    }
                    if (Boolean.TRUE.equals(itemRequest.getIsGetItem())) {
                        // Query get items by CategoryItemMapping IDs in batch
                        List<DiscountBxgyItem> getItems = discountBxgyItemRepository
                                .findByGetItemMappingIdsAndMenuId(categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
                        for (DiscountBxgyItem bxgyItem : getItems) {
                            Discount discount = bxgyItem.getDiscount();
                            if (discount != null && 
                                orderValidationService.isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId)) {
                                bxgyDiscountIds.add(discount.getId());
                            }
                        }
                    }
                }
            }
            
            // Add BXGY discount IDs to the list if not already present
            for (UUID bxgyDiscountId : bxgyDiscountIds) {
                if (!discountIds.contains(bxgyDiscountId)) {
                    discountIds.add(bxgyDiscountId);
                }
            }
        }
        
        return discountIds;
    }

    /**
     * Calculates subtotal for a set of ordered items/combos including BXGY application.
     * <p>
     * BXGY is prioritized such that items participating in BXGY sets are priced at base price (with price overrides
     * applied if available) and do not also receive item/category discounts. Get-item free quantities are treated as
     * request-driven ({@code freeQuantity} comes from the request, capped to quantity).
     * </p>
     *
     * @param orderedItems         ordered items to price (nullable; treated as empty)
     * @param orderedCombos        ordered combos to price (may be null)
     * @param menuId               menu identifier
     * @param restaurantId         restaurant context (discount validity/overrides)
     * @param activeOverrideIndex  active price overrides index (optional)
     * @return BXGY calculation result including subtotal, per-item pricing maps, and applied BXGY summaries
     */
    @Override
    public BxgyCalculationResult calculateSubTotalWithBxgyDiscounts(
            List<OrderedItemRequest> orderedItems,
            List<OrderedComboRequest> orderedCombos, 
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        BigDecimal subTotal = BigDecimal.ZERO;
        Map<UUID, BigDecimal> itemPrices = new HashMap<>();
        Map<UUID, BigDecimal> getItemPrices = new HashMap<>(); // Separate map for get item prices (for items that are both buy and get)
        List<BxgyDiscountInfo> appliedBxgyDiscounts = new ArrayList<>();
        List<DiscountUsageSummary> discountUsages = new ArrayList<>();
        List<ComboTaxItem> comboTaxItems = new ArrayList<>();
        Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest = new HashMap<>();
        
        // Helper class to pass getItemPrices to applyBxgyDiscount
        GetItemPricesHolder getItemPricesHolder = new GetItemPricesHolder();
        
        // Handle null or empty orderedItems - allow orders with only combos
        if (orderedItems == null) {
            orderedItems = new ArrayList<>();
        }
        
        // First, identify which items have BXGY discount to skip item/category discount calculation
        Map<UUID, List<OrderedItemRequest>> buyItemsByDiscount = new HashMap<>();
        Map<UUID, List<OrderedItemRequest>> getItemsByDiscount = new HashMap<>();
        Map<UUID, CategoryItemMapping> categoryMappingCache = new HashMap<>();
        Set<UUID> itemsWithBxgyDiscount = new HashSet<>(); // Track items that have BXGY discount
        
        for (OrderedItemRequest itemRequest : orderedItems) {
            // Ensure BXGY discount IDs are included when isBuyItem or isGetItem is true
            List<UUID> updatedDiscountIds = ensureBxgyDiscountIdsIncluded(itemRequest, menuId, restaurantId);
            if (!updatedDiscountIds.equals(itemRequest.getDiscountIds())) {
                itemRequest.setDiscountIds(updatedDiscountIds);
            }
            
            // Get CategoryItemMapping for cache
            CategoryItemMapping categoryItemMapping = getCategoryItemMapping(menuId, itemRequest.getItemId());
            if (categoryItemMapping != null) {
                categoryMappingCache.put(itemRequest.getItemId(), categoryItemMapping);
            }
            
            // Group by BXGY discount using request flags and track items with BXGY
            if (itemRequest.getDiscountIds() != null && !itemRequest.getDiscountIds().isEmpty()) {
                for (UUID discountId : itemRequest.getDiscountIds()) {
                    Discount discount = discountRepository.findById(discountId).orElse(null);
                    if (discount != null && discount.getDiscountType() == DiscountType.BXGY) {
                        // Mark this item as having BXGY discount - skip item/category discounts
                        itemsWithBxgyDiscount.add(itemRequest.getItemId());
                        
                        if (Boolean.TRUE.equals(itemRequest.getIsBuyItem())) {
                            buyItemsByDiscount.computeIfAbsent(discountId, k -> new ArrayList<>())
                                .add(itemRequest);
                        }
                        if (Boolean.TRUE.equals(itemRequest.getIsGetItem())) {
                            getItemsByDiscount.computeIfAbsent(discountId, k -> new ArrayList<>())
                                .add(itemRequest);
                        }
                    }
                }
            }
        }
        
        // Calculate item prices - for items with BXGY, calculate BASE PRICE ONLY (no item/category discounts)
        Map<Integer, DiscountCalculationResult> itemDiscountResultsByIndex = new HashMap<>();
        Map<UUID, DiscountCalculationResult> itemDiscountResults = new HashMap<>();
        for (int i = 0; i < orderedItems.size(); i++) {
            OrderedItemRequest itemRequest = orderedItems.get(i);
            DiscountCalculationResult discountResult;
            
            // If item has BXGY discount, calculate BASE PRICE ONLY (no item/category discounts)
            if (itemsWithBxgyDiscount.contains(itemRequest.getItemId())) {
                // Calculate base price only - no item/category discounts when BXGY is active
                Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemRequest.getItemId()));
                
                BigDecimal basePrice = BigDecimal.valueOf(item.getBasePrice());
                
                // Apply price override if available
                if (restaurantId != null && activeOverrideIndex != null) {
                    CategoryItemMapping categoryMapping = categoryMappingCache.get(itemRequest.getItemId());
                    if (categoryMapping != null) {
                        List<MenuCategoryMapping> itemMcms = List.of(categoryMapping.getMenuCategoryMapping());
                        Double effectiveBasePrice = priceOverrideHelper.resolveEffectiveBasePrice(
                            item.getBasePrice(), menuId, itemMcms, activeOverrideIndex);
                        basePrice = BigDecimal.valueOf(effectiveBasePrice);
                    }
                }
                
                BigDecimal totalPrice = basePrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
                // Return result with originalPrice = finalPrice (no discounts applied)
                discountResult = new DiscountCalculationResult(totalPrice, totalPrice, null, null);
                log.debug("BXGY Item - Calculating BASE PRICE ONLY (no item/category discounts) for item: {}, BasePrice: {}, TotalPrice: {}", 
                    itemRequest.getItemId(), basePrice, totalPrice);
            } else {
                // Regular item - calculate with item/category discounts
                if (restaurantId != null && activeOverrideIndex != null) {
                    discountResult = calculateItemPriceWithOverride(
                            menuId, 
                            itemRequest.getItemId(), 
                            itemRequest.getQuantity(),
                            restaurantId,
                            activeOverrideIndex);
                } else {
                    discountResult = calculateItemPrice(menuId, itemRequest.getItemId(), itemRequest.getQuantity());
                }

                // Track item/category discount usage for reporting
                if (discountResult != null
                        && discountResult.getAppliedDiscount() != null
                        && discountResult.getDiscountAmount() != null
                        && discountResult.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    String discountType;
                    String appliedTo;
                    if (discountResult.getDiscountLevel() != null) {
                        appliedTo = discountResult.getDiscountLevel().name();
                        switch (discountResult.getDiscountLevel()) {
                            case ITEM -> discountType = "Item";
                            case CATEGORY -> discountType = "Category";
                            default -> discountType = "Item";
                        }
                    } else {
                        appliedTo = "ITEM";
                        discountType = "Item";
                    }
                    discountUsages.add(new DiscountUsageSummary(
                            discountResult.getAppliedDiscount(),
                            discountType,
                            appliedTo,
                            discountResult.getDiscountAmount()
                    ));
                }
            }
            
            itemDiscountResultsByIndex.put(i, discountResult);
            if (!itemDiscountResults.containsKey(itemRequest.getItemId())) {
                itemDiscountResults.put(itemRequest.getItemId(), discountResult);
            }
        }
        
        // Process BXGY discounts using request flags
        for (UUID discountId : buyItemsByDiscount.keySet()) {
            List<OrderedItemRequest> buyItems = buyItemsByDiscount.get(discountId);
            List<OrderedItemRequest> getItems = getItemsByDiscount.get(discountId);
            
            if (getItems != null && !getItems.isEmpty()) {
                Discount discount = discountRepository.findById(discountId).orElse(null);
                if (discount != null && orderValidationService.isDiscountValidForMenuAndTime(menuId, discountId, restaurantId)) {
                    // Apply BXGY logic
                    BxgyDiscountInfo bxgyInfo = applyBxgyDiscount(
                        buyItems, getItems, discount, itemDiscountResults, itemDiscountResultsByIndex, orderedItems, categoryMappingCache, getItemPricesHolder, bxgyInfoByRequest
                    );
                    
                    if (bxgyInfo != null) {
                        appliedBxgyDiscounts.add(bxgyInfo);
                        // Track BXGY discount usage for reporting
                        if (bxgyInfo.getTotalSavings() != null
                                && bxgyInfo.getTotalSavings().compareTo(BigDecimal.ZERO) > 0) {
                            discountUsages.add(new DiscountUsageSummary(
                                    bxgyInfo.getDiscount(),
                                    "Item",          // BXGY acts at item level
                                    "ITEM",
                                    bxgyInfo.getTotalSavings()
                            ));
                        }
                        // Update item prices with BXGY pricing
                        for (Map.Entry<UUID, BigDecimal> priceEntry : bxgyInfo.getItemPrices().entrySet()) {
                            itemPrices.put(priceEntry.getKey(), priceEntry.getValue());
                        }
                        // Copy get item prices from the holder (populated in applyBxgyDiscount)
                        getItemPrices.putAll(getItemPricesHolder.getPrices());
                    }
                }
            }
        }
        
        // Calculate final subtotal - CORRECTED LOGIC
        log.info("========== STARTING calculateSubTotalWithBxgyDiscounts - Processing {} items ==========", orderedItems.size());
        log.debug("Processing {} items for subtotal calculation", orderedItems.size());
        for (int i = 0; i < orderedItems.size(); i++) {
            OrderedItemRequest itemRequest = orderedItems.get(i);
            log.debug("Processing item: ID={}, Quantity={}, Modifiers={}",
                itemRequest.getItemId(), itemRequest.getQuantity(),
                itemRequest.getOrderedItemModifiers() != null ? itemRequest.getOrderedItemModifiers().size() : 0);

            // Special handling for BXGY get items
            boolean isBxgyGetItem = Boolean.TRUE.equals(itemRequest.getIsGetItem());
            
            // Calculate modifier prices PER ITEM
            BigDecimal modifierPricePerItem = BigDecimal.ZERO;
            if (itemRequest.getOrderedItemModifiers() != null) {
                for (OrderedItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                    for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                        ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, LocaleContextHolder.getLocale())));
                        // Null-safe price handling: treat null prices as zero
                        BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                        modifierPricePerItem = modifierPricePerItem.add(modifierItemPrice);
                    }
                }
            }
            
            // Calculate total modifier price for this item
            BigDecimal totalModifierPrice = modifierPricePerItem.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            
            BigDecimal totalItemAmount;
            BigDecimal totalDiscountedItemAmount = BigDecimal.ZERO;
            BigDecimal totalItemPrice = BigDecimal.ZERO;
            if (isBxgyGetItem) {
                DiscountCalculationResult discountResult = itemDiscountResultsByIndex.get(i);
                
                if (discountResult == null) {
                    discountResult = itemDiscountResults.get(itemRequest.getItemId());
                }
                
                BigDecimal basePricePerUnit = BigDecimal.ZERO;
                BigDecimal basePriceTotalForAll = BigDecimal.ZERO;
                
                // Get currency for formatting
                String currency = restaurantChainConfigProperties.getChain().getCurrency();
                
                if (discountResult != null) {
                    BigDecimal originalPriceForDiscountResult = discountResult.getOriginalPrice();
                    int quantityInDiscountResult = itemRequest.getQuantity();
                    basePricePerUnit = originalPriceForDiscountResult
                        .divide(BigDecimal.valueOf(quantityInDiscountResult), 10, configuredDivideRoundingMode());
                    basePricePerUnit = CurrencyFormatter.formatAmount(basePricePerUnit, currency);
                    basePriceTotalForAll = CurrencyFormatter.formatAmount(
                        basePricePerUnit.multiply(BigDecimal.valueOf(itemRequest.getQuantity())), 
                        currency);
                } else {
                    Item item = itemRepository.findById(itemRequest.getItemId()).orElse(null);
                    if (item != null) {
                        basePricePerUnit = CurrencyFormatter.formatAmount(
                            BigDecimal.valueOf(item.getBasePrice()), 
                            currency);
                        basePriceTotalForAll = CurrencyFormatter.formatAmount(
                            basePricePerUnit.multiply(BigDecimal.valueOf(itemRequest.getQuantity())), 
                            currency);
                    }
                }
                
                // Calculate paid quantity based on request: paidQuantity = quantity - freeQuantity
                int freeQty = itemRequest.getFreeQuantity() != null ? itemRequest.getFreeQuantity() : 0;
                if (freeQty > itemRequest.getQuantity()) {
                    freeQty = itemRequest.getQuantity(); // safety cap
                }
                int paidQuantity = Math.max(0, itemRequest.getQuantity() - freeQty);
                
                // Calculate paid item price: base unit price * paid quantity
                BigDecimal paidItemPrice = CurrencyFormatter.formatAmount(
                    basePricePerUnit.multiply(BigDecimal.valueOf(paidQuantity)), 
                    currency);
                
                // totalItemAmount = base price * total quantity + modifier price * quantity (for all items)
                // This shows the full price for all items (including free and paid)
                totalItemAmount = basePriceTotalForAll.add(totalModifierPrice);
                
                // totalDiscountedItemAmount = paid item price (for this specific request) + modifier price * quantity
                // Modifiers apply to all items (both free and paid)
                totalDiscountedItemAmount = paidItemPrice.add(totalModifierPrice);
                totalItemPrice = paidItemPrice; // For logging purposes
                
            } else {
                // Check if BXGY discount was applied to this item
                BigDecimal totalDiscountedPrice;
                if (itemPrices.containsKey(itemRequest.getItemId())) {
                    // Use BXGY-adjusted price if available
                    BigDecimal priceFromMap = itemPrices.get(itemRequest.getItemId());
                    // For buy items, if the price in map is 0, it means this item was also a get item
                    // In that case, we need to calculate the price from discount results
                    if (priceFromMap.compareTo(BigDecimal.ZERO) == 0) {
                        // Price is 0 in map, likely because same item is also a get item
                        // Calculate the actual price for this specific request row.
                        // Use index-based lookup first to support duplicate itemIds with different quantities.
                        DiscountCalculationResult discountResult = itemDiscountResultsByIndex.get(i);
                        if (discountResult == null) {
                            discountResult = itemDiscountResults.get(itemRequest.getItemId());
                        }
                        if (discountResult == null) {
                            if (restaurantId != null && activeOverrideIndex != null) {
                                discountResult = calculateItemPriceWithOverride(
                                        menuId, 
                                        itemRequest.getItemId(), 
                                        itemRequest.getQuantity(),
                                        restaurantId,
                                        activeOverrideIndex);
                            } else {
                                discountResult = calculateItemPrice(menuId, itemRequest.getItemId(), itemRequest.getQuantity());
                            }
                        }
                        totalDiscountedPrice = discountResult.getFinalPrice();
                        log.debug("Item {} is both buy and get - using calculated price for buy item: {}", itemRequest.getItemId(), totalDiscountedPrice);
                    } else {
                        totalDiscountedPrice = priceFromMap;
                        log.debug("Using BXGY-adjusted price for item {}: {}", itemRequest.getItemId(), totalDiscountedPrice);
                    }
                    totalItemPrice = totalDiscountedPrice;
                } else {
                    // Use the already calculated discount result from the map to avoid redundant calculation.
                    // IMPORTANT: prefer index-based lookup to avoid wrong totals when same itemId appears
                    // multiple times with different quantities/modifiers in a single request.
                    DiscountCalculationResult discountResult = itemDiscountResultsByIndex.get(i);
                    if (discountResult == null) {
                        discountResult = itemDiscountResults.get(itemRequest.getItemId());
                    }
                    if (discountResult == null) {
                        // Fallback: calculate if not found in map (shouldn't happen)
                        if (restaurantId != null && activeOverrideIndex != null) {
                            discountResult = calculateItemPriceWithOverride(
                                    menuId, 
                                    itemRequest.getItemId(), 
                                    itemRequest.getQuantity(),
                                    restaurantId,
                                    activeOverrideIndex);
                        } else {
                            discountResult = calculateItemPrice(menuId, itemRequest.getItemId(), itemRequest.getQuantity());
                        }
                    }
                    totalDiscountedPrice = discountResult.getFinalPrice(); // This is the total discounted price for this item
                    totalItemPrice = totalDiscountedPrice;
                    log.debug("Using regular discount price for item {}: {}", itemRequest.getItemId(), totalDiscountedPrice);
                }
                
                // Calculate total item amount (item price + modifiers)
                totalItemAmount = totalDiscountedPrice.add(totalModifierPrice);
                // For buy items: totalDiscountedItemAmount = discounted item price + modifier price * quantity
                totalDiscountedItemAmount = totalItemAmount;
            }
            
            log.info("CALCULATION IN calculateSubTotalWithBxgyDiscounts - Item: {}, Quantity: {}, IsGetItem: {}", 
                itemRequest.getItemId(), itemRequest.getQuantity(), isBxgyGetItem);
            log.info("  - Total Item Price (for all quantity): {}", totalItemPrice);
            log.info("  - Modifier Price Per Item: {}", modifierPricePerItem);
            log.info("  - Total Modifier Price: {} = {} × {}", totalModifierPrice, modifierPricePerItem, itemRequest.getQuantity());
            log.info("  - Total Item Amount: {} = {} + {}", totalItemAmount, totalItemPrice, totalModifierPrice);
            log.info("  - Total Discounted Item Amount (for subtotal): {}", totalDiscountedItemAmount);
            
            // CRITICAL: Use totalDiscountedItemAmount for subtotal calculation (sum of discounted amounts for both get and buy items)
            // DO NOT use totalItemAmount - that includes base price for get items which should be free
            BigDecimal subTotalBefore = subTotal;
            // Ensure totalDiscountedItemAmount is set (should never be null due to initialization)
            if (totalDiscountedItemAmount == null) {
                log.error("ERROR: totalDiscountedItemAmount is null for item {}! Using totalModifierPrice as fallback.", itemRequest.getItemId());
                totalDiscountedItemAmount = totalModifierPrice;
            }
            subTotal = subTotal.add(totalDiscountedItemAmount);
            log.info("  - Subtotal before adding this item: {}, Subtotal after: {} (added totalDiscountedItemAmount: {})", 
                subTotalBefore, subTotal, totalDiscountedItemAmount);
            log.info("  - VERIFICATION: totalItemAmount={}, totalDiscountedItemAmount={}, using totalDiscountedItemAmount for subtotal", 
                totalItemAmount, totalDiscountedItemAmount);
            log.debug("Running subtotal after adding item {}: {}", itemRequest.getItemId(), subTotal);
        }
        
        // Add combo prices to subtotal (combos don't have discounts)
        if (orderedCombos != null && !orderedCombos.isEmpty()) {
            for (OrderedComboRequest comboRequest : orderedCombos) {
                Combo combo = comboRepository.findById(comboRequest.getComboId())
                    .orElseThrow(() -> new RuntimeException("Combo not found: " + comboRequest.getComboId()));
                
                // Calculate the actual combo price based on combo type
                BigDecimal comboPrice;
                if (combo.getType() == ComboType.FIXED) {
                    // FIXED combos use base price
                    comboPrice = combo.getBasePrice().multiply(BigDecimal.valueOf(comboRequest.getQuantity()));
                } else if (combo.getType() == ComboType.CHOICE) {
                    // CHOICE combos need complex calculation
                    comboPrice = calculateChoiceComboPrice(combo, comboRequest, Locale.ENGLISH);
                } else if (combo.getType() == ComboType.MIXED) {
                    // MIXED combos need complex calculation
                    comboPrice = calculateMixedComboPrice(combo, comboRequest, Locale.ENGLISH);
                } else {
                    // Fallback to base price
                    comboPrice = combo.getBasePrice().multiply(BigDecimal.valueOf(comboRequest.getQuantity()));
                }
                
                subTotal = subTotal.add(comboPrice);

                // Build combo tax items (effective item amounts inside this combo) for alcoholic/non-alcoholic breakdown
                try {
                    String currency = restaurantChainConfigProperties.getChain().getCurrency();
                    Locale userLocale = LocaleContextHolder.getLocale();
                    comboTaxItems.addAll(buildComboTaxItemsForBreakdown(combo, comboRequest, comboPrice, currency, userLocale));
                } catch (Exception e) {
                    // If extraction fails, do not break order pricing; fall back to treating entire combo as non-alcoholic
                    comboTaxItems.add(new ComboTaxItem(comboPrice, AlcoholType.NON_ALCOHOLIC));
                }
            }
        }
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        BigDecimal formattedSubTotal = CurrencyFormatter.formatAmount(subTotal, currency);
        log.info("  - Formatted subtotal: {}", formattedSubTotal);
        log.info("  - paidQuantitiesByRequest map size: {}, keys: {}", 
            getItemPricesHolder.getPaidQuantitiesByRequest().size(), getItemPricesHolder.getPaidQuantitiesByRequest().keySet());
        log.info("  - bxgyInfoByRequest map size: {}, keys: {}", 
            bxgyInfoByRequest.size(), bxgyInfoByRequest.keySet());
        log.info("========== END calculateSubTotalWithBxgyDiscounts ==========");
        return new BxgyCalculationResult(formattedSubTotal, itemPrices, getItemPrices, appliedBxgyDiscounts, getItemPricesHolder.getPaidQuantitiesByRequest(), comboTaxItems, bxgyInfoByRequest, discountUsages);
    }

    /**
     * Calculate price for CHOICE type combos
     * 
     * Formula: totalComboPrice = (comboBasePrice + itemPriceAdjustment + modifierPrice) × quantity
     * 
     * itemPriceAdjustment calculation per CHOICE group:
     * - If default item IS in selection (multi-select with default):
     *   - Default item: add 0
     *   - Other selected items: add full itemBasePrice
     * - If default item is NOT in selection (replace/upgrade):
     *   - Each selected item: add (itemBasePrice - defaultItemPrice)
     */
    @Override
    public BigDecimal calculateChoiceComboPrice(Combo combo, OrderedComboRequest comboRequest, Locale userLocale) {
        // Step 1: Get combo base price from HQ admin
        BigDecimal comboBasePrice = combo.getBasePrice() != null ? combo.getBasePrice() : BigDecimal.ZERO;
        
        // Step 2: Calculate itemPriceAdjustment for CHOICE groups only
        BigDecimal itemPriceAdjustment = calculateItemPriceAdjustmentForChoiceCombo(combo, comboRequest, userLocale);
        
        // Step 3: Calculate modifier price (sum of all selected modifier prices)
        BigDecimal modifierPrice = calculateModifierPriceForChoiceCombo(comboRequest, userLocale);
        
        // Step 4: Calculate final combo price per unit
        // Formula: comboBasePrice + itemPriceAdjustment + modifierPrice
        BigDecimal finalComboPricePerUnit = comboBasePrice.add(itemPriceAdjustment).add(modifierPrice);
        
        // Step 5: Apply quantity and format according to currency
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        BigDecimal finalComboPrice = CurrencyFormatter.formatAmount(
            finalComboPricePerUnit.multiply(BigDecimal.valueOf(comboRequest.getQuantity())), 
            currency);
        
        log.info("CHOICE Combo Price Calculation - Combo: {}, Base: {}, Item Adjustment: {}, Modifier: {}, Final Per Unit: {}, Final Total: {}", 
            combo.getComboId(), comboBasePrice, itemPriceAdjustment, modifierPrice, finalComboPricePerUnit, finalComboPrice);
        
        return finalComboPrice;
    }
    
    /**
     * Calculate item price adjustment for CHOICE combo based on frontend logic
     * Only processes CHOICE groups (FIXED groups don't contribute to itemPriceAdjustment)
     */
    private BigDecimal calculateItemPriceAdjustmentForChoiceCombo(Combo combo, OrderedComboRequest comboRequest, Locale userLocale) {
        BigDecimal totalAdjustment = BigDecimal.ZERO;
        
        // Process each CHOICE group in the combo
        for (ComboGroup comboGroup : combo.getComboGroups()) {
            if (comboGroup.getGroupType() != ComboGroupType.CHOICE) {
                // FIXED groups don't contribute to itemPriceAdjustment
                continue;
            }
            
            // Find the default item for this CHOICE group
            ComboItemMapping defaultMapping = comboGroup.getComboItemMappings().stream()
                .filter(mapping -> mapping.getIsDefault() != null && mapping.getIsDefault())
                .findFirst()
                .orElse(null);
            
            // Make final for use in lambda
            final UUID defaultItemId;
            final BigDecimal defaultItemPrice;
            if (defaultMapping != null) {
                defaultItemId = defaultMapping.getCategoryItemMapping().getItem().getId();
                defaultItemPrice = BigDecimal.valueOf(defaultMapping.getCategoryItemMapping().getItem().getBasePrice());
            } else {
                defaultItemId = null;
                defaultItemPrice = BigDecimal.ZERO;
            }
            
            // Find the corresponding group request
            OrderedComboGroupRequest groupRequest = comboRequest.getComboGroups().stream()
                .filter(gr -> gr.getComboGroupId().equals(comboGroup.getComboGroupId()))
                .findFirst()
                .orElse(null);
            
            if (groupRequest == null || groupRequest.getOrderedItems() == null || groupRequest.getOrderedItems().isEmpty()) {
                continue;
            }
            
            // Check if default item is in the current selection
            final UUID finalDefaultItemId = defaultItemId; // Final copy for lambda
            boolean defaultInSelection = finalDefaultItemId != null && groupRequest.getOrderedItems().stream()
                .anyMatch(item -> item.getItemId().equals(finalDefaultItemId));
            
            // Calculate adjustment for each selected item in this group
            for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
                Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                
                BigDecimal itemBasePrice = BigDecimal.valueOf(item.getBasePrice());
                UUID currentItemId = item.getId();
                
                if (defaultInSelection) {
                    // Case 3.1: Default IS in selection (multi-select with default)
                    if (currentItemId.equals(finalDefaultItemId)) {
                        // Default item: add 0
                        totalAdjustment = totalAdjustment.add(BigDecimal.ZERO);
                    } else {
                        // Other selected items: add full itemBasePrice
                        totalAdjustment = totalAdjustment.add(itemBasePrice);
                    }
                } else {
                    // Case 3.2: Default is NOT in selection (replace/upgrade)
                    if (defaultItemPrice.compareTo(BigDecimal.ZERO) > 0) {
                        // Add difference: itemBasePrice - defaultItemPrice
                        BigDecimal difference = itemBasePrice.subtract(defaultItemPrice);
                        totalAdjustment = totalAdjustment.add(difference);
                    } else {
                        // No default item price, treat as full price
                        totalAdjustment = totalAdjustment.add(itemBasePrice);
                    }
                }
            }
        }
        
        return totalAdjustment;
    }
    
    /**
     * Calculate modifier price for CHOICE combo (sum of all selected modifier prices)
     */
    private BigDecimal calculateModifierPriceForChoiceCombo(OrderedComboRequest comboRequest, Locale userLocale) {
        BigDecimal totalModifierPrice = BigDecimal.ZERO;
        
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            List<OrderedComboItemRequest> orderedItems = groupRequest != null ? groupRequest.getOrderedItems() : null;
            boolean skipGroup = orderedItems == null;
            if (skipGroup) {
                continue;
            }

            totalModifierPrice = totalModifierPrice.add(sumModifierPricesForOrderedItems(orderedItems, userLocale));
        }
        
        return totalModifierPrice;
    }

    private BigDecimal sumModifierPricesForOrderedItems(List<OrderedComboItemRequest> orderedItems, Locale userLocale) {
        if (orderedItems == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderedComboItemRequest itemRequest : orderedItems) {
            List<OrderedComboItemModifierRequest> orderedModifiers =
                    itemRequest != null ? itemRequest.getOrderedItemModifiers() : null;
            if (orderedModifiers == null) {
                continue;
            }
            List<OrderedComboItemModifierRequest> nonNullOrderedModifiers = orderedModifiers;
            for (OrderedComboItemModifierRequest modifierRequest : nonNullOrderedModifiers) {
                List<UUID> modifierItemIds = modifierRequest != null ? modifierRequest.getModifierItemIds() : null;
                if (modifierItemIds == null) {
                    continue;
                }
                List<UUID> nonNullModifierItemIds = modifierItemIds;
                for (UUID modifierItemId : nonNullModifierItemIds) {
                    ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                    BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                    total = total.add(modifierItemPrice);
                }
            }
        }
        return total;
    }

    /**
     * Calculate price for MIXED type combos
     */
    @Override
    public BigDecimal calculateMixedComboPrice(Combo combo, OrderedComboRequest comboRequest, Locale userLocale) {
        // Step 1: Calculate default items price (sum of all default items from CHOICE groups)
        BigDecimal defaultItemsPrice = calculateDefaultItemsPriceForMixed(combo);
        
        // Step 2: Calculate fixed items price (sum of all items from FIXED groups with their modifiers)
        BigDecimal fixedItemsPrice = calculateFixedItemsPriceForMixed(combo);
        
        // Step 3: Get combo base price from HQ admin
        BigDecimal comboBasePrice = combo.getBasePrice();
        
        // Step 4: Calculate difference amount (combo base price vs default + fixed)
        BigDecimal totalDefaultAndFixedPrice = defaultItemsPrice.add(fixedItemsPrice);
        BigDecimal differenceAmount = comboBasePrice.subtract(totalDefaultAndFixedPrice);
        
        // Step 5: Calculate selected items price (FIXED + CHOICE items)
        BigDecimal choiceItemsPrice = calculateSelectedItemsPriceForMixed(comboRequest, userLocale);
        BigDecimal selectedItemsPrice = fixedItemsPrice.add(choiceItemsPrice);
        
        // Step 6: Calculate final combo price per unit
        // Handle both positive and negative difference amounts properly
        BigDecimal finalComboPricePerUnit;
        if (differenceAmount.compareTo(BigDecimal.ZERO) < 0) {
            // Negative difference: add it (which effectively subtracts)
            // Example: 100 + (-200) = -100
            finalComboPricePerUnit = selectedItemsPrice.add(differenceAmount);
        } else {
            // Positive difference: subtract it
            // Example: 100 - 200 = -100
            finalComboPricePerUnit = selectedItemsPrice.subtract(differenceAmount);
        }
        
        // Step 7: Apply quantity and format according to currency
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        BigDecimal finalComboPrice = CurrencyFormatter.formatAmount(
            finalComboPricePerUnit.multiply(BigDecimal.valueOf(comboRequest.getQuantity())), 
            currency);
        
        log.info("MIXED Combo Price Calculation - Combo: {}, Default Items: {}, Fixed Items: {}, Total Default+Fixed: {}, Combo Base: {}, Difference: {}, Choice Items: {}, Selected Items: {}, Final Per Unit: {}, Final Total: {}", 
            combo.getComboId(), defaultItemsPrice, fixedItemsPrice, totalDefaultAndFixedPrice, comboBasePrice, differenceAmount, choiceItemsPrice, selectedItemsPrice, finalComboPricePerUnit, finalComboPrice);
        
        return finalComboPrice;
    }

    /**
     * Calculate default items price for CHOICE combos
     */
    private BigDecimal calculateDefaultItemsPrice(Combo combo) {
        BigDecimal totalDefaultPrice = BigDecimal.ZERO;
        
        for (ComboGroup comboGroup : combo.getComboGroups()) {
            if (comboGroup.getGroupType() == ComboGroupType.CHOICE) {
                // Find default items for this CHOICE group
                List<ComboItemMapping> defaultMappings = comboGroup.getComboItemMappings().stream()
                    .filter(mapping -> mapping.getIsDefault() != null && mapping.getIsDefault())
                    .collect(Collectors.toList());
                
                for (ComboItemMapping defaultMapping : defaultMappings) {
                    Item item = defaultMapping.getCategoryItemMapping().getItem();
                    totalDefaultPrice = totalDefaultPrice.add(BigDecimal.valueOf(item.getBasePrice()));
                    
                    // No default modifiers for CHOICE combos - only item base price
                }
            }
        }
        
        return totalDefaultPrice;
    }

    /**
     * Calculate selected items price for CHOICE combos
     */
    private BigDecimal calculateSelectedItemsPrice(OrderedComboRequest comboRequest, Locale userLocale) {
        BigDecimal totalSelectedPrice = BigDecimal.ZERO;
        
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
                Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                
                // Add item base price
                totalSelectedPrice = totalSelectedPrice.add(BigDecimal.valueOf(item.getBasePrice()));
                
                // Add modifiers price
                if (itemRequest.getOrderedItemModifiers() != null) {
                    for (OrderedComboItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                        for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                            ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                            // Null-safe price handling: treat null prices as zero
                            BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                            totalSelectedPrice = totalSelectedPrice.add(modifierItemPrice);
                        }
                    }
                }
            }
        }
        
        return totalSelectedPrice;
    }

    /**
     * Build effective item amounts inside a combo for tax breakdown.
     *
     * Constraints:
     * - Do NOT change existing combo price calculation logic (we replicate the same math here for per-unit values)
     * - Do NOT change tax calculation logic; we only feed additional amounts into the breakdown.
     */
    private List<ComboTaxItem> buildComboTaxItemsForBreakdown(
            Combo combo,
            OrderedComboRequest comboRequest,
            BigDecimal comboPriceTotal,
            String currency,
            Locale userLocale) {

        if (combo == null || comboRequest == null || comboRequest.getQuantity() == null || comboRequest.getQuantity() <= 0) {
            return Collections.emptyList();
        }

        int quantity = comboRequest.getQuantity();

        // Extract raw per-unit item prices inside combo (base + modifiers) + alcohol type
        List<RawComboTaxItem> rawItems;
        if (combo.getType() == ComboType.FIXED) {
            rawItems = extractRawItemsForFixedCombo(combo, userLocale);
        } else if (combo.getType() == ComboType.CHOICE) {
            rawItems = extractRawItemsFromChoiceRequest(comboRequest, userLocale);
        } else if (combo.getType() == ComboType.MIXED) {
            rawItems = new ArrayList<>();
            rawItems.addAll(extractRawItemsForFixedCombo(combo, userLocale)); // FIXED groups with predefined modifiers
            rawItems.addAll(extractRawItemsFromChoiceRequest(comboRequest, userLocale)); // selected CHOICE items + selected modifiers
        } else {
            // Unknown type, fall back to treating entire combo as non-alcoholic
            return Collections.singletonList(new ComboTaxItem(comboPriceTotal, AlcoholType.NON_ALCOHOLIC));
        }

        BigDecimal selectedItemsPrice = rawItems.stream()
                .map(r -> r.rawAmountPerUnit != null ? r.rawAmountPerUnit : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Compute final combo price per unit using the same existing logic (without modifying existing methods)
        BigDecimal finalComboPricePerUnit;
        if (combo.getType() == ComboType.FIXED) {
            finalComboPricePerUnit = combo.getBasePrice() != null ? combo.getBasePrice() : BigDecimal.ZERO;
        } else if (combo.getType() == ComboType.CHOICE) {
            BigDecimal defaultItemsPrice = calculateDefaultItemsPrice(combo);
            BigDecimal comboBasePrice = combo.getBasePrice() != null ? combo.getBasePrice() : BigDecimal.ZERO;
            // Use same formula as calculateChoiceComboPrice: max(comboBasePrice, selectedItemsPrice - defaultItemsPrice)
            BigDecimal itemsDifference = selectedItemsPrice.subtract(defaultItemsPrice);
            finalComboPricePerUnit = comboBasePrice.max(itemsDifference);
        } else { // MIXED
            BigDecimal defaultItemsPrice = calculateDefaultItemsPriceForMixed(combo);
            BigDecimal fixedItemsPrice = calculateFixedItemsPriceForMixed(combo);
            BigDecimal comboBasePrice = combo.getBasePrice() != null ? combo.getBasePrice() : BigDecimal.ZERO;
            BigDecimal totalDefaultAndFixedPrice = defaultItemsPrice.add(fixedItemsPrice);
            BigDecimal differenceAmount = comboBasePrice.subtract(totalDefaultAndFixedPrice);

            // selectedItemsPrice already includes fixed group raw + selected choice raw
            finalComboPricePerUnit = differenceAmount.compareTo(BigDecimal.ZERO) < 0
                    ? selectedItemsPrice.add(differenceAmount)
                    : selectedItemsPrice.subtract(differenceAmount);
        }

        // Step: Calculate scale factor (final per-unit / selectedItemsPrice)
        if (selectedItemsPrice.compareTo(BigDecimal.ZERO) == 0) {
            // Skip scaling, treat entire combo as non-alcoholic
            return Collections.singletonList(new ComboTaxItem(comboPriceTotal, AlcoholType.NON_ALCOHOLIC));
        }

        BigDecimal scaleFactor = finalComboPricePerUnit.divide(selectedItemsPrice, 10, configuredDivideRoundingMode());

        List<ComboTaxItem> result = new ArrayList<>();
        for (RawComboTaxItem raw : rawItems) {
            BigDecimal rawAmount = raw.rawAmountPerUnit != null ? raw.rawAmountPerUnit : BigDecimal.ZERO;
            BigDecimal effectivePerUnit = rawAmount.multiply(scaleFactor);
            BigDecimal total = effectivePerUnit.multiply(BigDecimal.valueOf(quantity));
            BigDecimal formatted = CurrencyFormatter.formatAmount(total, currency);
            AlcoholType alcoholType = raw.alcoholType != null ? raw.alcoholType : AlcoholType.NON_ALCOHOLIC;
            result.add(new ComboTaxItem(formatted, alcoholType));
        }

        return result;
    }

    /**
     * Extracts raw per-unit amounts (base + predefined modifiers) for FIXED combo groups.
     * <p>
     * This is used to compute effective combo item amounts for alcoholic/non-alcoholic tax breakdown.
     * </p>
     *
     * @param combo       combo entity with groups/mappings
     * @param userLocale  locale used for localized exception messages
     * @return list of raw per-unit amounts tagged with alcohol type
     * @throws ResponseStatusException if required modifier/item entities cannot be resolved
     */
    private List<RawComboTaxItem> extractRawItemsForFixedCombo(Combo combo, Locale userLocale) {
        if (combo == null || combo.getComboGroups() == null) return Collections.emptyList();

        List<RawComboTaxItem> rawItems = new ArrayList<>();
        for (ComboGroup group : combo.getComboGroups()) {
            if (group == null || group.getGroupType() != ComboGroupType.FIXED) continue;
            if (group.getComboItemMappings() == null) continue;

            for (ComboItemMapping mapping : group.getComboItemMappings()) {
                if (mapping == null || mapping.getCategoryItemMapping() == null || mapping.getCategoryItemMapping().getItem() == null) continue;
                Item item = mapping.getCategoryItemMapping().getItem();

                BigDecimal itemPrice = item.getBasePrice() != null ? BigDecimal.valueOf(item.getBasePrice()) : BigDecimal.ZERO;

                // Include predefined modifiers
                List<ComboItemModifier> predefinedModifiers = comboItemModifierRepository.findByComboItemMappingId(mapping.getId());
                if (predefinedModifiers != null) {
                    for (ComboItemModifier modifier : predefinedModifiers) {
                        if (modifier == null || modifier.getModifierItem() == null) continue;
                        BigDecimal modifierPrice = modifier.getModifierItem().getPrice() != null ? modifier.getModifierItem().getPrice() : BigDecimal.ZERO;
                        itemPrice = itemPrice.add(modifierPrice);
                    }
                }

                AlcoholType alcoholType = item.getAlcoholType();
                rawItems.add(new RawComboTaxItem(itemPrice, alcoholType));
            }
        }
        return rawItems;
    }

    /**
     * Extracts raw per-unit amounts (base + selected modifiers) from the ordered combo request (CHOICE selections).
     * <p>
     * This is used to compute effective combo item amounts for alcoholic/non-alcoholic tax breakdown based on the
     * customer-selected combo items and modifiers.
     * </p>
     *
     * @param comboRequest ordered combo request containing chosen groups/items/modifiers
     * @param userLocale   locale used for localized exception messages
     * @return list of raw per-unit amounts tagged with alcohol type
     * @throws ResponseStatusException when selected item/modifier references cannot be resolved
     */
    private List<RawComboTaxItem> extractRawItemsFromChoiceRequest(OrderedComboRequest comboRequest, Locale userLocale) {
        if (comboRequest == null || comboRequest.getComboGroups() == null) return Collections.emptyList();

        List<RawComboTaxItem> rawItems = new ArrayList<>();
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            if (groupRequest == null || groupRequest.getOrderedItems() == null) continue;

            for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
                if (itemRequest == null || itemRequest.getItemId() == null) continue;
                Item item = itemRepository.findById(itemRequest.getItemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("item.name.not.found", userLocale)));

                BigDecimal itemPrice = item.getBasePrice() != null ? BigDecimal.valueOf(item.getBasePrice()) : BigDecimal.ZERO;

                // Include selected modifiers
                if (itemRequest.getOrderedItemModifiers() != null) {
                    for (OrderedComboItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                        if (modifierRequest == null || modifierRequest.getModifierItemIds() == null) continue;
                        for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                            if (modifierItemId == null) continue;
                            ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                            messageUtil.getMessage("modifier.item.name.not.found", userLocale)));
                            BigDecimal modifierPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                            itemPrice = itemPrice.add(modifierPrice);
                        }
                    }
                }

                rawItems.add(new RawComboTaxItem(itemPrice, item.getAlcoholType()));
            }
        }
        return rawItems;
    }

    /**
     * Calculate default items price for MIXED combos (only from CHOICE groups)
     */
    private BigDecimal calculateDefaultItemsPriceForMixed(Combo combo) {
        BigDecimal totalDefaultPrice = BigDecimal.ZERO;
        
        for (ComboGroup comboGroup : combo.getComboGroups()) {
            if (comboGroup.getGroupType() == ComboGroupType.CHOICE) {
                // Find default items for this CHOICE group
                List<ComboItemMapping> defaultMappings = comboGroup.getComboItemMappings().stream()
                    .filter(mapping -> mapping.getIsDefault() != null && mapping.getIsDefault())
                    .collect(Collectors.toList());
                
                for (ComboItemMapping mapping : defaultMappings) {
                    Item item = mapping.getCategoryItemMapping().getItem();
                    BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
                    totalDefaultPrice = totalDefaultPrice.add(itemPrice);
                }
            }
        }
        
        return totalDefaultPrice;
    }

    /**
     * Calculate fixed items price for MIXED combos (from FIXED groups with modifiers)
     */
    private BigDecimal calculateFixedItemsPriceForMixed(Combo combo) {
        BigDecimal totalFixedPrice = BigDecimal.ZERO;
        
        for (ComboGroup comboGroup : combo.getComboGroups()) {
            if (comboGroup.getGroupType() == ComboGroupType.FIXED) {
                // Get all items from FIXED group with their predefined modifiers
                List<ComboItemMapping> fixedMappings = comboGroup.getComboItemMappings();
                
                for (ComboItemMapping mapping : fixedMappings) {
                    Item item = mapping.getCategoryItemMapping().getItem();
                    BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
                    
                    // Add predefined modifiers price
                    List<ComboItemModifier> predefinedModifiers = comboItemModifierRepository
                        .findByComboItemMappingId(mapping.getId());
                    
                    for (ComboItemModifier modifier : predefinedModifiers) {
                        itemPrice = itemPrice.add(modifier.getModifierItem().getPrice());
                    }
                    
                    totalFixedPrice = totalFixedPrice.add(itemPrice);
                }
            }
        }
        
        return totalFixedPrice;
    }

    /**
     * Calculate selected items price for MIXED combos (only from CHOICE groups)
     */
    private BigDecimal calculateSelectedItemsPriceForMixed(OrderedComboRequest comboRequest, Locale userLocale) {
        BigDecimal totalSelectedPrice = BigDecimal.ZERO;
        
        // Only process CHOICE groups for selected items (FIXED items are handled separately)
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            ComboGroup comboGroup = comboGroupRepository.findById(groupRequest.getComboGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("combo.group.not.found", userLocale)));
            
            // Only process CHOICE groups for selected items
            if (comboGroup.getGroupType() == ComboGroupType.CHOICE) {
                for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
                    Item item = itemRepository.findById(itemRequest.getItemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                    
                    BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
                    
                    // Add selected modifiers price
                    if (itemRequest.getOrderedItemModifiers() != null) {
                        for (OrderedComboItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                            // Validate modifier group exists
                            modifierGroupRepository.findById(modifierRequest.getModifierGroupId())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("modifier.group.not.found", userLocale)));
                            
                            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                                ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                                // Null-safe price handling: treat null prices as zero
                                BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                                itemPrice = itemPrice.add(modifierItemPrice);
                            }
                        }
                    }
                    
                    totalSelectedPrice = totalSelectedPrice.add(itemPrice);
                }
            }
        }
        
        return totalSelectedPrice;
    }

    /**
     * Applies a BXGY discount to the provided buy/get request sets.
     * <p>
     * This implementation is request-driven for free quantities: per-item free quantity is taken from each get-item
     * request (capped to its quantity). When BXGY is active, buy/get items use base pricing (no item/category
     * discounts), and savings are computed from the base unit price for free quantities.
     * </p>
     *
     * @param buyItems                  buy items participating in the discount
     * @param getItems                  get items participating in the discount
     * @param discount                  BXGY discount definition
     * @param itemDiscountResults       item pricing results map (used as a fallback by item id)
     * @param itemDiscountResultsByIndex item pricing results keyed by request index (preferred for disambiguation)
     * @param allOrderedItems           original ordered-items list (for index matching)
     * @param categoryMappingCache      cache of item → category mapping to reduce lookups
     * @param getItemPricesHolder       holder for get-item prices map (used for items that are both buy and get)
     * @param bxgyInfoByRequest         output map of request-key → BXGY metadata (application id/role/free qty)
     * @return BXGY discount info including calculated savings and per-item prices, or {@code null} when not applicable
     * @throws IllegalArgumentException when quantities are invalid
     * @throws IllegalStateException when required pricing results cannot be resolved
     */
    @Override
    public BxgyDiscountInfo applyBxgyDiscount(
            List<OrderedItemRequest> buyItems,
            List<OrderedItemRequest> getItems,
            Discount discount,
            Map<UUID, DiscountCalculationResult> itemDiscountResults,
            Map<Integer, DiscountCalculationResult> itemDiscountResultsByIndex,
            List<OrderedItemRequest> allOrderedItems,
            Map<UUID, CategoryItemMapping> categoryMappingCache,
            GetItemPricesHolder getItemPricesHolder,
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest) {
        
        Map<UUID, BigDecimal> getItemPricesMap = getItemPricesHolder.getPrices();
        
        // Generate unique discount application ID for this BXGY application
        UUID discountApplicationId = UUID.randomUUID();
        log.info("Generated discount_application_id: {} for BXGY discount: {}", discountApplicationId, discount.getId());
        
        int buyQuantity = discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1;
        int getQuantity = discount.getGetQuantity() != null ? discount.getGetQuantity() : 1;
        
        // Calculate total quantities (aggregate across all buy/get items)
        int totalBuyQuantity = buyItems.stream().mapToInt(OrderedItemRequest::getQuantity).sum();
        int totalGetQuantity = getItems.stream().mapToInt(OrderedItemRequest::getQuantity).sum();

        // ==================== USE REQUEST FREE QUANTITIES AS-IS ====================
        // Sum free quantities provided in the request for all GET items.
        int requestedFreeQuantity = getItems.stream()
                .map(OrderedItemRequest::getFreeQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        // Ensure free quantity does not exceed total GET quantity
        int freeQuantity = Math.min(requestedFreeQuantity, totalGetQuantity);
        int paidGetQuantity = Math.max(0, totalGetQuantity - freeQuantity);

        // Buy sets are still calculated for logging/analytics, but no longer drive free quantity calculation.
        int buySets = (int) Math.floor((double) totalBuyQuantity / buyQuantity);
        
        log.info("BXGY Calculation (request-driven freeQuantity) - DiscountId: {}, BuyQuantity: {}, GetQuantity: {}, TotalBuyQuantity: {}, TotalGetQuantity: {}, BuySets: {}, RequestedFreeQuantity: {}, EffectiveFreeQuantity: {}, PaidGetQuantity: {}", 
            discount.getId(), buyQuantity, getQuantity, totalBuyQuantity, totalGetQuantity, buySets, requestedFreeQuantity, freeQuantity, paidGetQuantity);
        
        if (buySets == 0) {
            log.info("No BXGY applicable - buySets is 0 for discountId: {}", discount.getId());
            return null; // No BXGY applicable
        }
        
        Map<UUID, BigDecimal> itemPrices = new HashMap<>();
        BigDecimal totalSavings = BigDecimal.ZERO;
        
        // Process buy items (BXGY discount applied - NO item/category discounts for buy items when BXGY is active)
        for (OrderedItemRequest buyItem : buyItems) {
            if (buyItem.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero for price calculation.");
            }
            // Find the correct discount result by finding the index of this buy item in the original list
            // This is more reliable than string-based keys
            DiscountCalculationResult discountResult = null;
            int itemIndex = -1;
            for (int i = 0; i < allOrderedItems.size(); i++) {
                OrderedItemRequest item = allOrderedItems.get(i);
                // Match by reference or by all key fields to find the exact request
                if (item == buyItem || 
                    (item.getItemId().equals(buyItem.getItemId()) &&
                     item.getQuantity().equals(buyItem.getQuantity()) &&
                     Boolean.TRUE.equals(item.getIsBuyItem()) == Boolean.TRUE.equals(buyItem.getIsBuyItem()) &&
                     Boolean.TRUE.equals(item.getIsGetItem()) == Boolean.TRUE.equals(buyItem.getIsGetItem()))) {
                    itemIndex = i;
                    discountResult = itemDiscountResultsByIndex.get(i);
                    break;
                }
            }
            // Fallback to itemId-based map if not found (for backward compatibility)
            if (discountResult == null) {
                discountResult = itemDiscountResults.get(buyItem.getItemId());
            }
            if (discountResult == null) {
                throw new IllegalStateException("Discount result not found for buy item: " + buyItem.getItemId());
            }
            // Get currency for formatting
            String currency = restaurantChainConfigProperties.getChain().getCurrency();
            // When BXGY is applied, use BASE PRICE (originalPrice) - NO item/category discounts
            // Only one discount type applies: BXGY OR item/category, not both
            BigDecimal unitPrice = discountResult.getOriginalPrice().divide(BigDecimal.valueOf(buyItem.getQuantity()), 10, configuredDivideRoundingMode());
            BigDecimal totalPrice = CurrencyFormatter.formatAmount(
                unitPrice.multiply(BigDecimal.valueOf(buyItem.getQuantity())), 
                currency);
            itemPrices.put(buyItem.getItemId(), totalPrice);
            log.debug("BXGY Buy Item[{}] - ItemId: {}, Quantity: {}, UnitPrice (base price, no item/category discount): {}, TotalPrice: {}", 
                itemIndex, buyItem.getItemId(), buyItem.getQuantity(), unitPrice, totalPrice);
            
            // Store BXGY info for this buy item request
            boolean isBuyItem = Boolean.TRUE.equals(buyItem.getIsBuyItem());
            boolean isGetItem = Boolean.TRUE.equals(buyItem.getIsGetItem());
            String requestKey = String.format("%s:%d:%s:%s", 
                buyItem.getItemId().toString(), 
                buyItem.getQuantity(),
                isBuyItem,
                isGetItem);
            com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo = new com.gulfnet.shared_library.model.response.dto.BxgyItemInfo(
                discountApplicationId, discount.getId(), com.gulfnet.shared_library.enums.BxgyRole.BUY, 0);
            bxgyInfoByRequest.put(requestKey, bxgyInfo);
            log.info("STORED BXGY INFO FOR BUY ITEM - RequestKey: {}, DiscountApplicationId: {}, DiscountId: {}, BxgyRole: BUY", 
                requestKey, discountApplicationId, discount.getId());
        }
        
        // Process get items - use free quantities provided in the request (no recalculation)
        // First, collect per-item free quantity for each get item
        Map<OrderedItemRequest, Integer> freeQuantityByGetItem = new HashMap<>();
        int totalDistributedFreeQuantity = 0;
        
        if (freeQuantity > 0 && !getItems.isEmpty()) {
            for (OrderedItemRequest getItem : getItems) {
                int itemQuantity = getItem.getQuantity();
                // Use freeQuantity from request as-is (null treated as 0), but never more than item quantity
                int itemFreeQuantity = getItem.getFreeQuantity() != null ? getItem.getFreeQuantity() : 0;
                int finalFreeQuantity = Math.min(itemFreeQuantity, itemQuantity);

                freeQuantityByGetItem.put(getItem, finalFreeQuantity);
                totalDistributedFreeQuantity += finalFreeQuantity;

                log.info("REQUEST FREE QUANTITY PER ITEM - ItemId: {}, Quantity: {}, RequestFreeQuantity: {}, EffectiveFreeQuantity: {}, PaidQuantity: {}", 
                        getItem.getItemId(), itemQuantity, itemFreeQuantity, finalFreeQuantity,
                        itemQuantity - finalFreeQuantity);
            }

            log.info("REQUEST FREE QUANTITY SUMMARY - TotalRequestedFreeQuantity: {}, TotalEffectiveFreeQuantity: {}, GetItemsCount: {}", 
                requestedFreeQuantity, totalDistributedFreeQuantity, getItems.size());
        }
        
        // Process all get items - always calculate get item price for the separate map
        // This allows us to handle items that are both buy and get correctly
        for (OrderedItemRequest getItem : getItems) {
            // Always calculate get item price and store in separate map
            // Only add to main itemPrices map if item is NOT also a buy item
            boolean isAlsoBuyItem = itemPrices.containsKey(getItem.getItemId());
            
            // Always calculate get item price (even if item is also a buy item)
            // Find the correct discount result by finding the index of this get item in the original list
            DiscountCalculationResult discountResult = null;
            int itemIndex = -1;
            for (int i = 0; i < allOrderedItems.size(); i++) {
                OrderedItemRequest item = allOrderedItems.get(i);
                // Match by reference or by all key fields to find the exact request
                if (item == getItem || 
                    (item.getItemId().equals(getItem.getItemId()) &&
                     item.getQuantity().equals(getItem.getQuantity()) &&
                     Boolean.TRUE.equals(item.getIsBuyItem()) == Boolean.TRUE.equals(getItem.getIsBuyItem()) &&
                     Boolean.TRUE.equals(item.getIsGetItem()) == Boolean.TRUE.equals(getItem.getIsGetItem()))) {
                    itemIndex = i;
                    discountResult = itemDiscountResultsByIndex.get(i);
                    break;
                }
            }
            // Fallback to itemId-based map if not found (for backward compatibility)
            if (discountResult == null) {
                discountResult = itemDiscountResults.get(getItem.getItemId());
            }
            
            if (discountResult != null) {
                    // For BXGY get items: Only BXGY discount applies, NO item/category discounts
                    // 1. Free items (based on floor formula) = 0 price (BXGY discount)
                    // 2. Paid items = charged at BASE PRICE (no item/category discounts when BXGY is active)
                    BigDecimal basePriceTotal = discountResult.getOriginalPrice();
                    
                    // Calculate base unit price (normal price without any discounts)
                    BigDecimal baseUnitPrice = basePriceTotal
                        .divide(BigDecimal.valueOf(getItem.getQuantity()), 10, configuredDivideRoundingMode());
                    
                    // Get free quantity for this get item (taken directly from request mapping)
                    int itemQuantity = getItem.getQuantity();
                    int freeItemQuantity = freeQuantityByGetItem.getOrDefault(getItem, 0);
                    int paidItemQuantity = itemQuantity - freeItemQuantity;
                    
                    // Get currency for formatting
                    String currency = restaurantChainConfigProperties.getChain().getCurrency();
                    
                    // Calculate total price: 
                    // - Free items: 0 (handled by BXGY discount)
                    // - Paid items: charged at BASE PRICE (no item/category discounts - only one discount type applies)
                    // Use higher precision for calculation, then format according to currency
                    BigDecimal totalPrice = CurrencyFormatter.formatAmount(
                        baseUnitPrice.multiply(BigDecimal.valueOf(paidItemQuantity)), 
                        currency);
                    
                    // CRITICAL: Store per-request paid quantity information for accurate retrieval in createNewOrderedItem
                    // Create a unique key for this specific request: itemId:quantity:isBuyItem:isGetItem
                    // Use explicit boolean conversion to ensure consistent string format
                    boolean isBuyItem = Boolean.TRUE.equals(getItem.getIsBuyItem());
                    boolean isGetItem = Boolean.TRUE.equals(getItem.getIsGetItem());
                    String requestKey = String.format("%s:%d:%s:%s", 
                        getItem.getItemId().toString(), 
                        getItem.getQuantity(),
                        isBuyItem,
                        isGetItem);
                    getItemPricesHolder.getPaidQuantitiesByRequest().put(requestKey, paidItemQuantity);
                    log.info("STORED PAID QUANTITY - RequestKey: {}, PaidQuantity: {}, ItemId: {}, Quantity: {}, IsBuyItem: {}, IsGetItem: {}, FreeQuantity: {}", 
                        requestKey, paidItemQuantity, getItem.getItemId(), getItem.getQuantity(), 
                        isBuyItem, isGetItem, freeItemQuantity);
                    
                    // Store BXGY info for this get item request
                    com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo = new com.gulfnet.shared_library.model.response.dto.BxgyItemInfo(
                        discountApplicationId, discount.getId(), com.gulfnet.shared_library.enums.BxgyRole.GET, freeItemQuantity);
                    bxgyInfoByRequest.put(requestKey, bxgyInfo);
                    log.info("STORED BXGY INFO FOR GET ITEM - RequestKey: {}, DiscountApplicationId: {}, DiscountId: {}, BxgyRole: GET, FreeQuantity: {}", 
                        requestKey, discountApplicationId, discount.getId(), freeItemQuantity);
                    
                    // CRITICAL: Aggregate prices if same itemId appears multiple times (don't overwrite)
                    // This handles cases where the same item is added multiple times with different quantities
                    BigDecimal existingPrice = getItemPricesMap.getOrDefault(getItem.getItemId(), BigDecimal.ZERO);
                    BigDecimal aggregatedPrice = CurrencyFormatter.formatAmount(
                        existingPrice.add(totalPrice), 
                        currency);
                    getItemPricesMap.put(getItem.getItemId(), aggregatedPrice);
                    
                    // Only add to main itemPrices map if item is NOT also a buy item
                    // Also aggregate here if same item appears multiple times
                    if (!isAlsoBuyItem) {
                        BigDecimal existingItemPrice = itemPrices.getOrDefault(getItem.getItemId(), BigDecimal.ZERO);
                        BigDecimal aggregatedItemPrice = CurrencyFormatter.formatAmount(
                            existingItemPrice.add(totalPrice), 
                            currency);
                        itemPrices.put(getItem.getItemId(), aggregatedItemPrice);
                    }
                    
                    log.info("BXGY Get Item Price Calculation - DiscountId: {}, ItemId: {}, Quantity: {}, Free: {}, Paid: {}, BaseUnitPrice: {}, TotalPrice (paid items at base price, no item/category discount): {}, IsAlsoBuyItem: {}", 
                        discount.getId(), getItem.getItemId(), itemQuantity, freeItemQuantity, paidItemQuantity, baseUnitPrice, totalPrice, isAlsoBuyItem);
                    
                    // Calculate savings only for free items (use base price for savings calculation)
                    // Only BXGY discount applies - no item/category discounts when BXGY is active
                    BigDecimal savings = baseUnitPrice.multiply(BigDecimal.valueOf(freeItemQuantity));
                    totalSavings = totalSavings.add(savings);
                    
                    log.info("BXGY Get Item[{}] - DiscountId: {}, ItemId: {}, Quantity: {}, Free: {}, Paid: {}, BaseUnitPrice: {}, TotalPrice: {}, Savings: {}", 
                        itemIndex, discount.getId(), getItem.getItemId(), itemQuantity, freeItemQuantity, paidItemQuantity, 
                        baseUnitPrice, totalPrice, savings);
                } else {
                    // If no discount result found, set price to 0 (shouldn't happen, but handle gracefully)
                    getItemPricesMap.put(getItem.getItemId(), BigDecimal.ZERO);
                    if (!isAlsoBuyItem) {
                        itemPrices.put(getItem.getItemId(), BigDecimal.ZERO);
                    }
                    log.warn("No discount result found for get item: {}", getItem.getItemId());
                }
        }
        
        log.info("BXGY Calculation - Discount: {}, Buy Qty: {}, Get Qty: {}, Sets: {}, Free: {}, Paid: {}, Savings: {}, DiscountApplicationId: {}",
            discount.getId(), totalBuyQuantity, totalGetQuantity, buySets, freeQuantity, paidGetQuantity, totalSavings, discountApplicationId);
        
        return new BxgyDiscountInfo(discount, buySets, totalSavings, itemPrices, freeQuantity, paidGetQuantity, discountApplicationId);
    }

    /**
     * Calculates final item pricing details including modifiers for display/storage.
     * <p>
     * Produces per-unit base/discounted prices (when applicable) and total amounts with/without discount, taking into
     * account modifier totals. For BXGY get-items, discounted per-unit price is treated as zero, while totals reflect
     * full base price for all items and a discounted total reflecting only the paid portion plus modifiers.
     * </p>
     *
     * @param item                     item entity being priced
     * @param itemRequest               request containing quantity, modifier selections, and BXGY flags
     * @param discountedItemPrice       total discounted item price (for non-BXGY discounts)
     * @param overriddenBasePricePerUnit overridden base unit price when price overrides apply (optional)
     * @param hasDiscount               whether an item/category discount applies (BXGY buy-items force this to false)
     * @param isBxgyGetItem             whether this item is a BXGY get-item (free/partially free)
     * @param paidQuantitiesByRequest   paid-quantity hints by request key (may be used by callers)
     * @param userLocale                locale for localized exception messages
     * @param discountResult            discount result context (may be null)
     * @param itemPrices                per-item pricing map (used to detect BXGY participation)
     * @return structured pricing breakdown for the item including modifier totals
     * @throws ResponseStatusException when modifier items cannot be resolved
     * @throws IllegalArgumentException when quantity is invalid
     */
    @Override
    public ItemPriceCalculationResult calculateItemPriceWithModifiers(
            Item item,
            OrderedItemRequest itemRequest,
            BigDecimal discountedItemPrice,
            BigDecimal overriddenBasePricePerUnit,
            boolean hasDiscount,
            boolean isBxgyGetItem,
            Map<String, Integer> paidQuantitiesByRequest,
            Locale userLocale,
            DiscountCalculationResult discountResult,
            Map<UUID, BigDecimal> itemPrices) {
        
        // Calculate modifier prices per item (consistent approach)
        BigDecimal modifierPricePerItem = BigDecimal.ZERO;
        if (itemRequest.getOrderedItemModifiers() != null) {
            for (OrderedItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                    ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                    // Null-safe price handling: treat null prices as zero
                    BigDecimal itemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                    modifierPricePerItem = modifierPricePerItem.add(itemPrice);
                }
            }
        }
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        // Calculate all prices with consistent rounding
        // For get items, always show the base price (even though item is free via BXGY)
        // Use overridden base price if provided, otherwise use item's base price
        BigDecimal basePricePerUnit = (overriddenBasePricePerUnit != null) 
                ? CurrencyFormatter.formatAmount(overriddenBasePricePerUnit, currency)
                : CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency);
        if (itemRequest.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero for price calculation.");
        }
        
        // Calculate total amounts
        BigDecimal totalModifierPrice = CurrencyFormatter.formatAmount(
            modifierPricePerItem.multiply(BigDecimal.valueOf(itemRequest.getQuantity())), 
            currency);
        
        // For BXGY get items, use the discountedItemPrice from itemPrices map
        // This may be 0 (all free) or a positive value (paid portion exists)
        BigDecimal discountedPricePerUnit = null;
        BigDecimal totalAmountWithoutDiscount;
        BigDecimal totalAmountWithDiscount = null;
        
        if (isBxgyGetItem) {
            // For get items: discountedPrice is always 0 per unit (they're free via BXGY, not percentage/amount discounted)
            discountedPricePerUnit = CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency);
            
            // totalItemAmount = base price * total quantity + modifier price * quantity
            // This shows the full price for all items (including free and paid)
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(
                basePricePerUnit.multiply(BigDecimal.valueOf(itemRequest.getQuantity())).add(totalModifierPrice), 
                currency);
            
            // Determine paid quantity purely from request:
            // paidQuantity = quantity - freeQuantity (capped at >= 0)
            int freeQty = itemRequest.getFreeQuantity() != null ? itemRequest.getFreeQuantity() : 0;
            if (freeQty > itemRequest.getQuantity()) {
                freeQty = itemRequest.getQuantity(); // safety cap
            }
            int paidQuantity = Math.max(0, itemRequest.getQuantity() - freeQty);
            
            // Calculate paid item price: base unit price * paid quantity
            BigDecimal paidItemPrice = CurrencyFormatter.formatAmount(
                basePricePerUnit.multiply(BigDecimal.valueOf(paidQuantity)), 
                currency);
            
            // totalDiscountedItemAmount = paid item price (for this specific request) + modifier price * quantity
            // Modifiers apply to all items (both free and paid), so include total modifier price
            totalAmountWithDiscount = CurrencyFormatter.formatAmount(
                paidItemPrice.add(totalModifierPrice), currency);
            
            log.info("CALCULATION GET ITEM PAID QUANTITY - ItemId: {}, Quantity: {}, FreeQuantity: {}, PaidQuantity: {}, PaidItemPrice: {}, BasePricePerUnit: {}, TotalModifierPrice: {}, TotalDiscountedItemAmount: {}", 
                itemRequest.getItemId(), itemRequest.getQuantity(), freeQty, paidQuantity, paidItemPrice, basePricePerUnit, totalModifierPrice, totalAmountWithDiscount);
        } else {
            // Regular items (buy items or non-BXGY items)
            // Check if this is a BXGY buy item - if so, NO item/category discounts should apply
            boolean isBxgyBuyItemOnly = Boolean.TRUE.equals(itemRequest.getIsBuyItem()) && 
                                       !Boolean.TRUE.equals(itemRequest.getIsGetItem()) &&
                                       itemPrices != null && itemPrices.containsKey(itemRequest.getItemId());
            
            // For BXGY buy items, set hasDiscount to false to ensure discountedPrice and totalDiscountedItemAmount are null
            // When BXGY discount is active, no item/category discounts apply
            if (isBxgyBuyItemOnly) {
                hasDiscount = false;
                discountedPricePerUnit = null; // Explicitly set to null for BXGY buy items
                log.info("CALCULATION BXGY Buy Item - Setting hasDiscount to false and discountedPricePerUnit to null for item: {}", itemRequest.getItemId());
            }
            
            if (hasDiscount) {
                discountedPricePerUnit = CurrencyFormatter.formatAmount(
                    discountedItemPrice.divide(BigDecimal.valueOf(itemRequest.getQuantity()), 10, configuredDivideRoundingMode()), 
                    currency);
            }
            
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(
                (basePricePerUnit.add(modifierPricePerItem)).multiply(BigDecimal.valueOf(itemRequest.getQuantity())), 
                currency);
            
            // Only set totalAmountWithDiscount if hasDiscount is true (not for BXGY buy items)
            if (hasDiscount && discountedPricePerUnit != null) {
                // Apply the discount factor to modifiers as well (extras should be discounted too for percent-based item/category discounts).
                // We infer the factor from base vs discounted unit prices to avoid needing the full Discount object here.
                BigDecimal discountFactor = BigDecimal.ONE;
                if (basePricePerUnit.compareTo(BigDecimal.ZERO) > 0) {
                    discountFactor = discountedPricePerUnit
                            .divide(basePricePerUnit, 10, configuredDivideRoundingMode());
                }
                BigDecimal discountedModifierPerUnit = CurrencyFormatter.formatAmount(
                        modifierPricePerItem.multiply(discountFactor),
                        currency);
                totalAmountWithDiscount = CurrencyFormatter.formatAmount(
                    (discountedPricePerUnit.add(discountedModifierPerUnit)).multiply(BigDecimal.valueOf(itemRequest.getQuantity())),
                    currency);
            } else {
                // For BXGY buy items or items without discount, totalAmountWithDiscount should be null
                totalAmountWithDiscount = null;
            }
        }
        
        // Detailed logging for price calculation
        log.info("PRICE CALCULATION DETAILS - Item: {}, Quantity: {}, HasDiscount: {}", item.getId(), itemRequest.getQuantity(), hasDiscount);
        log.info("  - Base Price Per Unit (overridden): {}", basePricePerUnit);
        log.info("  - Discounted Price Per Unit: {}", discountedPricePerUnit);
        log.info("  - Modifier Price Per Item: {}", modifierPricePerItem);
        log.info("  - Total Modifier Price: {}", totalModifierPrice);
        log.info("  - Total Amount Without Discount: {} = ({} + {}) × {}", 
                totalAmountWithoutDiscount, basePricePerUnit, modifierPricePerItem, itemRequest.getQuantity());
        if (hasDiscount) {
            log.info("  - Total Amount With Discount: {} = ({} + {}) × {}", 
                    totalAmountWithDiscount, discountedPricePerUnit, modifierPricePerItem, itemRequest.getQuantity());
        } else {
            log.info("  - Total Amount With Discount: null (no discount applied)");
        }
        
        return new ItemPriceCalculationResult(
            basePricePerUnit,
            discountedPricePerUnit,
            totalAmountWithoutDiscount,
            totalAmountWithDiscount
        );
    }

    /**
     * Check if BXGY discount exists for an item via database mappings (DiscountBxgyItem)
     * BXGY discounts are stored in DiscountBxgyItem table with buyItemMappingId/getItemMappingId
     * This uses the same approach as MenuServiceImpl - querying by CategoryItemMapping IDs in batch
     * This checks restaurant-level and menu-level mappings, respecting priority
     * 
     * @param menuId Menu ID
     * @param itemId Item ID
     * @param categoryItemMapping CategoryItemMapping for the item
     * @param restaurantId Restaurant ID (optional, for restaurant-level validation)
     * @return true if BXGY discount exists and is valid, false otherwise
     */
    private boolean checkBxgyDiscountExistsForItem(
            UUID menuId, 
            UUID itemId, 
            CategoryItemMapping categoryItemMapping,
            UUID restaurantId) {
        
        log.debug("Checking for BXGY discount for item {} in menu {} with restaurant {}", itemId, menuId, restaurantId);
        
        // Get ALL CategoryItemMappings for this item that belong to the menu (not just current categories)
        // This ensures we find BXGY discounts even if assigned using a different CategoryItemMapping
        List<CategoryItemMapping> allItemMappings = categoryItemMappingRepository
                .findByItem_Id(itemId)
                .stream()
                .filter(mapping -> mapping.getMenuCategoryMapping().getMenu().getId().equals(menuId))
                .collect(Collectors.toList());

        log.debug("Found {} CategoryItemMappings for item {} in menu {}", allItemMappings.size(), itemId, menuId);

        if (allItemMappings.isEmpty()) {
            log.debug("No CategoryItemMappings found for item {} in menu {}", itemId, menuId);
            return false;
        }

        // Extract CategoryItemMapping IDs for batch query (same approach as MenuServiceImpl)
        List<UUID> categoryItemMappingIds = allItemMappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());
        
        log.debug("Checking BXGY discounts for item {} in menu {} using {} CategoryItemMapping IDs", itemId, menuId, categoryItemMappingIds.size());

        // Query buy items by CategoryItemMapping IDs in batch - only finds discounts where the mapping ID is in discount_bxgy_item
        // This matches the approach used in MenuServiceImpl.calculateDiscountInfo()
        List<DiscountBxgyItem> buyItems = discountBxgyItemRepository.findByBuyItemMappingIdsAndMenuId(
                categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
        log.debug("Found {} buy items for BXGY discount for item {} using CategoryItemMapping IDs", buyItems.size(), itemId);
        
        for (DiscountBxgyItem bxgy : buyItems) {
            // Additional check for validity period and usage limits
            Discount discount = bxgy.getDiscount();
            if (discount != null) {
                log.debug("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                boolean isActive = orderValidationService.isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId);
                log.debug("Discount {} isActive: {}", discount.getId(), isActive);
                if (isActive) {
                    log.info("BXGY discount {} found via DiscountBxgyItem (buyItemMappingId) for item {} using CategoryItemMapping {}", 
                        discount.getId(), itemId, bxgy.getBuyItemMapping().getId());
                    return true;
                }
            }
        }
        
        // Query get items by CategoryItemMapping IDs in batch - only finds discounts where the mapping ID is in discount_bxgy_item
        List<DiscountBxgyItem> getItems = discountBxgyItemRepository.findByGetItemMappingIdsAndMenuId(
                categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
        log.debug("Found {} get items for BXGY discount for item {} using CategoryItemMapping IDs", getItems.size(), itemId);
        
        for (DiscountBxgyItem bxgy : getItems) {
            // Additional check for validity period and usage limits
            Discount discount = bxgy.getDiscount();
            if (discount != null) {
                log.debug("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                boolean isActive = orderValidationService.isDiscountValidForMenuAndTime(menuId, discount.getId(), restaurantId);
                log.debug("Discount {} isActive: {}", discount.getId(), isActive);
                if (isActive) {
                    log.info("BXGY discount {} found via DiscountBxgyItem (getItemMappingId) for item {} using CategoryItemMapping {}", 
                        discount.getId(), itemId, bxgy.getGetItemMapping().getId());
                    return true;
                }
            }
        }
        
        log.debug("No valid BXGY discount found for item {} via database mappings", itemId);
        return false;
    }
    
    /**
     * Calculate alcoholic and non-alcoholic breakdown from items and subtotal.
     * This method determines the proportion of alcoholic vs non-alcoholic items
     * to apply appropriate tax rates.
     */
    private AlcoholicBreakdown calculateAlcoholicBreakdown(
            List<OrderedItemRequest> orderedItems,
            BigDecimal subtotalAfterDiscount,
            BxgyCalculationResult bxgyResult,
            List<ComboTaxItem> comboTaxItems,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        
        boolean hasAnyItems = orderedItems != null && !orderedItems.isEmpty();
        boolean hasAnyComboItems = comboTaxItems != null && !comboTaxItems.isEmpty();
        if ((!hasAnyItems && !hasAnyComboItems) || subtotalAfterDiscount.compareTo(BigDecimal.ZERO) == 0) {
            // No items/combos or zero subtotal - treat as non-alcoholic
            return new AlcoholicBreakdown(
                BigDecimal.ZERO, subtotalAfterDiscount, BigDecimal.ZERO, BigDecimal.ONE);
        }
        
        BigDecimal alcoholicSubtotal = BigDecimal.ZERO;
        BigDecimal nonAlcoholicSubtotal = BigDecimal.ZERO;
        
        // IMPORTANT:
        // Do NOT use bxgyResult.getItemPrices()/getGetItemPrices() here.
        // Those are Map<itemId, amount> aggregates; if the same item appears multiple times in the request,
        // reading by itemId and summing per request line will double-count and inflate taxable splits.
        // Instead, compute amounts per request line (or use stored ordered_item amounts when available).
        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency()
                : null;
        Map<String, Integer> paidQuantitiesByRequest =
                bxgyResult != null && bxgyResult.getPaidQuantitiesByRequest() != null
                        ? bxgyResult.getPaidQuantitiesByRequest()
                        : new HashMap<>();
        
        if (orderedItems != null && !orderedItems.isEmpty()) {
            for (OrderedItemRequest itemRequest : orderedItems) {
                Item item = itemRepository.findById(itemRequest.getItemId()).orElse(null);
                if (item == null) continue;

                // Check if item is alcoholic
                boolean isAlcoholic = item.getAlcoholType() != null &&
                        item.getAlcoholType() == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC;

                // Get item price from BXGY result or calculate from stored items
                BigDecimal itemAmount = BigDecimal.ZERO;
                if (itemRequest.getOrderedItemId() != null) {
                    // For stored items, use stored amount
                    OrderedItem existingItem = orderedItemRepository.findById(itemRequest.getOrderedItemId()).orElse(null);
                    if (existingItem != null) {
                        // Exclude canceled items from breakdown (they should not contribute to taxable split)
                        if (existingItem.getItemStatus() != null && existingItem.getItemStatus() == ItemStatus.CANCELED) {
                            continue;
                        }
                        // Prefer snapshot alcohol type stored on ordered_item (stable even if Item changes later)
                        if (existingItem.getAlcoholType() != null) {
                            isAlcoholic = existingItem.getAlcoholType() == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC;
                        }
                        BigDecimal discounted = existingItem.getTotalDiscountedItemAmount();
                        if (discounted != null) {
                            itemAmount = discounted;
                        } else if (existingItem.getTotalItemAmount() != null) {
                            itemAmount = existingItem.getTotalItemAmount();
                        } else {
                            itemAmount = BigDecimal.ZERO;
                        }
                    }
                } else {
                    // New (non-stored) item request. Calculate per request line.
                    try {
                        DiscountCalculationResult discountResult;
                        if (restaurantId != null && activeOverrideIndex != null) {
                            discountResult = calculateItemPriceWithOverride(menuId, itemRequest.getItemId(), itemRequest.getQuantity(), restaurantId, activeOverrideIndex);
                        } else {
                            discountResult = calculateItemPrice(menuId, itemRequest.getItemId(), itemRequest.getQuantity());
                        }

                        boolean isBuyItem = Boolean.TRUE.equals(itemRequest.getIsBuyItem());
                        boolean isGetItem = Boolean.TRUE.equals(itemRequest.getIsGetItem());
                        String requestKey = String.format("%s:%d:%s:%s",
                                itemRequest.getItemId().toString(),
                                itemRequest.getQuantity(),
                                isBuyItem,
                                isGetItem);

                        if (isGetItem) {
                            // For BXGY get items, only paid quantity contributes to subtotal (free qty = 0).
                            // The subtotal logic charges PAID qty at BASE price (no item/category discounts when BXGY is active).
                            int qty = itemRequest.getQuantity() != null ? itemRequest.getQuantity() : 0;
                            int paidQty = paidQuantitiesByRequest.getOrDefault(requestKey, qty);
                            if (qty > 0 && discountResult.getOriginalPrice() != null) {
                                BigDecimal baseUnitPrice = discountResult.getOriginalPrice()
                                        .divide(BigDecimal.valueOf(qty), 10, configuredDivideRoundingMode());
                                BigDecimal unformatted = baseUnitPrice.multiply(BigDecimal.valueOf(paidQty));
                                itemAmount = currency != null
                                        ? CurrencyFormatter.formatAmount(unformatted, currency)
                                        : unformatted;
                            } else {
                                itemAmount = BigDecimal.ZERO;
                            }
                        } else {
                            // Normal/buy items: use final price (after applicable item/category discounts).
                            itemAmount = discountResult.getFinalPrice() != null ? discountResult.getFinalPrice() : BigDecimal.ZERO;
                        }

                        log.debug("Calculated per-request breakdown amount for item {} (key={}): {}", itemRequest.getItemId(), requestKey, itemAmount);
                    } catch (Exception e) {
                        log.warn("Failed to calculate per-request price for alcoholic breakdown item {}: {}", itemRequest.getItemId(), e.getMessage());
                        // If calculation fails, itemAmount remains zero
                    }
                }

                if (isAlcoholic) {
                    alcoholicSubtotal = alcoholicSubtotal.add(itemAmount);
                    log.debug("Alcoholic item {} added to alcoholic subtotal: {} (total so far: {})", 
                        itemRequest.getItemId(), itemAmount, alcoholicSubtotal);
                } else {
                    nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(itemAmount);
                    log.debug("Non-alcoholic item {} added to non-alcoholic subtotal: {} (total so far: {})", 
                        itemRequest.getItemId(), itemAmount, nonAlcoholicSubtotal);
                }
            }
        }
        
        log.info("Alcoholic breakdown calculation - Alcoholic subtotal: {}, Non-alcoholic subtotal: {}, Total: {}", 
            alcoholicSubtotal, nonAlcoholicSubtotal, alcoholicSubtotal.add(nonAlcoholicSubtotal));

        // Add combo effective item amounts to breakdown
        if (comboTaxItems != null && !comboTaxItems.isEmpty()) {
            for (ComboTaxItem comboItem : comboTaxItems) {
                if (comboItem == null || comboItem.getAmount() == null) continue;
                AlcoholType alcoholType = comboItem.getAlcoholType();
                if (alcoholType == AlcoholType.ALCOHOLIC) {
                    alcoholicSubtotal = alcoholicSubtotal.add(comboItem.getAmount());
                } else {
                    nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(comboItem.getAmount());
                }
            }
        }
        
        // If we couldn't determine from items, use the subtotal proportionally
        // This handles cases where item prices aren't available
        BigDecimal total = alcoholicSubtotal.add(nonAlcoholicSubtotal);
        BigDecimal alcoholicRatio = BigDecimal.ZERO;
        BigDecimal nonAlcoholicRatio = BigDecimal.ONE;
        
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            // Default to non-alcoholic if we can't determine
            nonAlcoholicSubtotal = subtotalAfterDiscount;
            alcoholicSubtotal = BigDecimal.ZERO;
            alcoholicRatio = BigDecimal.ZERO;
            nonAlcoholicRatio = BigDecimal.ONE;
            log.warn("Could not determine alcoholic breakdown from items - defaulting to non-alcoholic. Subtotal: {}", subtotalAfterDiscount);
        } else {
            // Scale to match the actual subtotal after discount
            BigDecimal scaleFactor = subtotalAfterDiscount.divide(total, 10, configuredDivideRoundingMode());
            alcoholicSubtotal = alcoholicSubtotal.multiply(scaleFactor);
            nonAlcoholicSubtotal = nonAlcoholicSubtotal.multiply(scaleFactor);
            
            // Recalculate ratios AFTER scaling to ensure they match the scaled subtotals exactly
            // This is critical for correct service charge allocation in tax base calculation
            BigDecimal scaledTotal = alcoholicSubtotal.add(nonAlcoholicSubtotal);
            if (scaledTotal.compareTo(BigDecimal.ZERO) > 0) {
                alcoholicRatio = alcoholicSubtotal.divide(scaledTotal, 10, configuredDivideRoundingMode());
                nonAlcoholicRatio = nonAlcoholicSubtotal.divide(scaledTotal, 10, configuredDivideRoundingMode());
                
                // Ensure ratios sum to 1.0 (handle rounding errors)
                BigDecimal ratioSum = alcoholicRatio.add(nonAlcoholicRatio);
                if (ratioSum.compareTo(BigDecimal.ONE) != 0) {
                    // Adjust non-alcoholic ratio to ensure sum equals 1.0
                    nonAlcoholicRatio = BigDecimal.ONE.subtract(alcoholicRatio);
                }
            }
            
            log.info("Alcoholic breakdown scaled - Alcoholic: {} (ratio: {}), Non-alcoholic: {} (ratio: {}), Scale factor: {}", 
                alcoholicSubtotal, alcoholicRatio, nonAlcoholicSubtotal, nonAlcoholicRatio, scaleFactor);
        }
        
        // Validate final breakdown
        BigDecimal finalTotal = alcoholicSubtotal.add(nonAlcoholicSubtotal);
        BigDecimal difference = finalTotal.subtract(subtotalAfterDiscount).abs();
        if (difference.compareTo(new BigDecimal("0.01")) > 0) {
            log.warn("Alcoholic breakdown total ({}) does not match subtotal after discount ({}) - difference: {}", 
                finalTotal, subtotalAfterDiscount, difference);
        }
        
        return new AlcoholicBreakdown(alcoholicSubtotal, nonAlcoholicSubtotal, alcoholicRatio, nonAlcoholicRatio);
    }
    
    /**
     * Calculate charge amount based on type (PERCENT or FLAT).
     * 
     * @param baseAmount The base amount to calculate charge on
     * @param value The charge value (percentage or flat amount)
     * @param type The charge type (PERCENT or FLAT)
     * @param currency Currency for formatting
     * @return The calculated charge amount (formatted)
     */
    private BigDecimal calculateChargeAmount(BigDecimal baseAmount, BigDecimal value, ChargeType type, String currency) {
        BigDecimal unformattedAmount = calculateChargeAmountUnformatted(baseAmount, value, type);
        return CurrencyFormatter.formatAmount(unformattedAmount, currency);
    }
    
    /**
     * Calculate charge amount WITHOUT formatting (for use in tax base calculations).
     * This ensures precision is maintained when calculating tax bases.
     * 
     * @param baseAmount The base amount to calculate charge on
     * @param value The charge value (percentage or flat amount)
     * @param type The charge type (PERCENT or FLAT)
     * @return The calculated charge amount (unformatted, with high precision)
     */
    private BigDecimal calculateChargeAmountUnformatted(BigDecimal baseAmount, BigDecimal value, ChargeType type) {
        if (type == null) {
            type = ChargeType.PERCENT; // Default to PERCENT if not specified
        }
        
        if (type == ChargeType.PERCENT) {
            // Percentage: (baseAmount * value) / 100
            return baseAmount.multiply(value).divide(BigDecimal.valueOf(100), 10, configuredDivideRoundingMode());
        } else {
            // Flat: use value directly
            return value;
        }
    }
}

