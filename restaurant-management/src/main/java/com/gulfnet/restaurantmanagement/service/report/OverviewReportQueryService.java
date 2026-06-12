package com.gulfnet.restaurantmanagement.service.report;

import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse;
import com.gulfnet.restaurantmanagement.util.PaymentMethodDisplaySupport;
import com.gulfnet.shared_library.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OverviewReportQueryService {

    private static final Logger logger = LoggerFactory.getLogger(OverviewReportQueryService.class);

    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final ItemTranslationRepository itemTranslationRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ComboTranslationRepository comboTranslationRepository;
    private final ItemRepository itemRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReportTranslationSupport reportTranslationSupport;
    private final PaymentMethodDisplaySupport paymentMethodDisplaySupport;

    private PaginationMetaData createPaginationMetaData(long totalElements, Integer page, Integer size) {
        int pageNum = (page != null && page > 0) ? page : 1;
        int pageSize = (size != null && size > 0) ? size : 10;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return PaginationMetaData.builder()
                .page(pageNum)
                .size(pageSize)
                .totalRecords(totalElements)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Calculates daily sales summary for a restaurant within a date range.
     * Includes total sales, total orders, total tables served, and average order value.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return DailySalesSummary with aggregated sales metrics
     */
    public ReportsOverviewResponse.DailySalesSummary getDailySalesSummary(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        List<Object[]> results = transactionRepository.getDailySalesSummary(
                restaurantId, startDate, endDate);

        if (results.isEmpty() || results.get(0) == null) {
            return ReportsOverviewResponse.DailySalesSummary.builder()
                    .totalSales(BigDecimal.ZERO)
                    .totalOrders(0L)
                    .totalTablesServed(0L)
                    .avgOrderValue(BigDecimal.ZERO)
                    .build();
        }

        Object[] row = results.get(0);
        BigDecimal totalSales = ((Number) row[0]).doubleValue() != 0 
                ? BigDecimal.valueOf(((Number) row[0]).doubleValue()) 
                : BigDecimal.ZERO;
        Long totalOrders = ((Number) row[1]).longValue();
        Long totalTablesServed = ((Number) row[2]).longValue();
        BigDecimal avgOrderValue = totalOrders > 0 
                ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ReportsOverviewResponse.DailySalesSummary.builder()
                .totalSales(totalSales)
                .totalOrders(totalOrders)
                .totalTablesServed(totalTablesServed)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    /**
     * Calculates payment types breakdown for a restaurant within a date range.
     * Groups sales by payment method (Cash, Card, UPI, etc.) and calculates percentages.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return list of PaymentTypeBreakdown with payment method, total sales, and percentage
     */
    public List<ReportsOverviewResponse.PaymentTypeBreakdown> getPaymentTypesBreakdown(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        List<Object[]> results = transactionRepository.getPaymentTypesBreakdown(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate total sales for percentage calculation
        BigDecimal totalSales = results.stream()
                .map(row -> BigDecimal.valueOf(((Number) row[1]).doubleValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return results.stream()
                .map(row -> {
                    String paymentMethod = (String) row[0];
                    BigDecimal sales = BigDecimal.valueOf(((Number) row[1]).doubleValue());
                    Double percentage = totalSales.compareTo(BigDecimal.ZERO) > 0
                            ? sales.divide(totalSales, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .doubleValue()
                            : 0.0;

                    // Map payment method to display name
                    String displayName = mapPaymentMethodToDisplayName(paymentMethod);

                    return ReportsOverviewResponse.PaymentTypeBreakdown.builder()
                            .paymentMethod(displayName)
                            .totalSales(sales)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Maps payment method code to human-readable display name.
     * Groups CREDIT_CARD and DEBIT_CARD as "Card".
     *
     * @param paymentMethod the payment method code (e.g., "CASH", "CREDIT_CARD", "UPI")
     * @return human-readable display name (e.g., "Cash", "Card", "Mobile Wallet")
     */
    private String mapPaymentMethodToDisplayName(String paymentMethod) {
        if (paymentMethod == null) {
            return "Unknown";
        }
        return paymentMethodDisplaySupport.toDisplayName(paymentMethod, LocaleContextHolder.getLocale());
    }

    private static String itemizedSalesMergeKey(ReportsOverviewResponse.ItemizedSalesItem item) {
        if (item.getItemCode() != null && !item.getItemCode().isBlank()) {
            return "code:" + item.getItemCode().trim().toLowerCase(Locale.ROOT);
        }
        return "name:" + (item.getItemName() != null ? item.getItemName() : "");
    }

    /**
     * Generates itemized sales report for a restaurant within a date range.
     * Combines regular items and combos, includes translations, and calculates percentages.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @param locale       locale code for localized item/category names
     * @return ItemizedSalesReport with paginated list of items/combos and sales metrics
     */
    public ReportsOverviewResponse.ItemizedSalesReport getItemizedSalesReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection, String locale) {

        // Fetch regular items (excluding combo items)
        List<Object[]> itemResults = orderedItemRepository.getItemizedSalesReport(
                restaurantId, startDate, endDate);

        // Fetch combos
        List<Object[]> comboResults = orderedComboRepository.getItemizedComboSalesReport(
                restaurantId, startDate, endDate);

        // Combine both lists
        List<ReportsOverviewResponse.ItemizedSalesItem> items = new ArrayList<>();

        // Process regular items
        if (!itemResults.isEmpty()) {
            // Get all item IDs and category IDs for batch translation lookup
            Set<UUID> itemIds = itemResults.stream()
                    .map(row -> (UUID) row[0])
                    .collect(Collectors.toSet());
            Set<UUID> categoryIds = itemResults.stream()
                    .map(row -> (UUID) row[1])
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch translations
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(itemIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(categoryIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

            Map<UUID, String> itemCodeByItemId = new HashMap<>();
            if (!itemIds.isEmpty()) {
                for (Item entity : itemRepository.findAllById(new ArrayList<>(itemIds))) {
                    itemCodeByItemId.put(entity.getId(), entity.getItemCode());
                }
            }

            // Map item results
            List<ReportsOverviewResponse.ItemizedSalesItem> itemList = itemResults.stream()
                    .map(row -> {
                        UUID itemId = (UUID) row[0];
                        UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                        Integer quantitySold = row[2] != null ? ((Number) row[2]).intValue() : 0;
                        BigDecimal unitPrice = row[3] != null 
                                ? BigDecimal.valueOf(((Number) row[3]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        BigDecimal itemTotalSales = row[4] != null 
                                ? BigDecimal.valueOf(((Number) row[4]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                        // Get item name from translations
                        String itemName = reportTranslationSupport.getItemName(itemId, itemTranslationsMap, locale);
                        // Get category name from translations
                        String categoryName = reportTranslationSupport.getCategoryName(categoryId, categoryTranslationsMap, locale);

                        return ReportsOverviewResponse.ItemizedSalesItem.builder()
                                .itemCode(itemCodeByItemId.get(itemId))
                                .itemName(itemName)
                                .category(categoryName)
                                .quantitySold(quantitySold)
                                .unitPrice(unitPrice)
                                .totalSales(itemTotalSales)
                                .percentageOfTotalSales(0.0) // Will be calculated after combining with combos
                                .build();
                    })
                    .collect(Collectors.toList());
            items.addAll(itemList);
        }

        // Process combos
        if (!comboResults.isEmpty()) {
            // Get all combo IDs and category IDs for batch translation lookup
            Set<UUID> comboIds = comboResults.stream()
                    .map(row -> (UUID) row[0])
                    .collect(Collectors.toSet());
            Set<UUID> comboCategoryIds = comboResults.stream()
                    .map(row -> (UUID) row[1])
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch combo translations
            Map<UUID, List<ComboTranslation>> comboTranslationsMap = comboIds.isEmpty()
                    ? Collections.emptyMap()
                    : comboTranslationRepository.findByComboComboIdIn(new ArrayList<>(comboIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCombo().getComboId()));

            // Batch fetch category translations for combos (reuse existing categoryTranslationsMap if available)
            Map<UUID, List<CategoryTranslation>> comboCategoryTranslationsMap = comboCategoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(comboCategoryIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

            // Map combo results
            List<ReportsOverviewResponse.ItemizedSalesItem> comboList = comboResults.stream()
                    .map(row -> {
                        UUID comboId = (UUID) row[0];
                        UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                        Integer quantitySold = row[2] != null ? ((Number) row[2]).intValue() : 0;
                        BigDecimal unitPrice = row[3] != null 
                                ? BigDecimal.valueOf(((Number) row[3]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        BigDecimal comboTotalSales = row[4] != null 
                                ? BigDecimal.valueOf(((Number) row[4]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                        // Get combo name from translations
                        String comboName = reportTranslationSupport.getComboName(comboId, comboTranslationsMap, locale);
                        // Get category name from translations
                        String categoryName = reportTranslationSupport.getCategoryName(categoryId, comboCategoryTranslationsMap, locale);

                        return ReportsOverviewResponse.ItemizedSalesItem.builder()
                                .itemCode(null)
                                .itemName(comboName)
                                .category(categoryName)
                                .quantitySold(quantitySold)
                                .unitPrice(unitPrice)
                                .totalSales(comboTotalSales)
                                .percentageOfTotalSales(0.0) // Will be calculated after combining
                                .build();
                    })
                    .collect(Collectors.toList());
            items.addAll(comboList);
        }

        // Remove inconsistent/non-sellable rows from report output.
        // These can occur when refund quantity and refund amount are out of sync in historical data.
        items = items.stream()
                .filter(item -> item.getQuantitySold() != null && item.getQuantitySold() > 0)
                .filter(item -> item.getTotalSales() != null && item.getTotalSales().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.ItemizedSalesReport.builder()
                    .items(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Calculate total sales for percentage calculation (after combining items and combos)
        BigDecimal totalSales = items.stream()
                .map(ReportsOverviewResponse.ItemizedSalesItem::getTotalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update percentages for all items
        items.forEach(item -> {
            Double percentage = totalSales.compareTo(BigDecimal.ZERO) > 0 && item.getTotalSales().compareTo(BigDecimal.ZERO) > 0
                    ? item.getTotalSales().divide(totalSales, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue()
                    : 0.0;
            item.setPercentageOfTotalSales(percentage);
        });

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.ItemizedSalesItem> comparator = switch (sortField) {
                case "itemname", "item_name" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getItemName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "itemcode", "item_code" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getItemCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "category" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getCategory,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "quantitysold", "quantity_sold" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getQuantitySold,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "unitprice", "unit_price" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getUnitPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalsales", "total_sales" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "percentageoftotalsales", "percentage_of_total_sales" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getPercentageOfTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalSales
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            items.sort(comparator);
        } else {
            // Default sorting: by totalSales descending
            items.sort(Comparator.comparing(
                    ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.ItemizedSalesItem> paginatedItems;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedItems = items;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, items.size());
            int toIndex = Math.min(fromIndex + pageSize, items.size());
            if (fromIndex >= items.size()) {
                paginatedItems = new ArrayList<>();
            } else {
                paginatedItems = items.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) items.size() / pageSize))
                    .totalRecords((long) items.size())
                    .build();
        }
        
        return ReportsOverviewResponse.ItemizedSalesReport.builder()
                .items(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) items.size())
                .metaData(metaData)
                .build();
    }
    /**
     * Generates table-wise sales report for a restaurant within a date range.
     * Aggregates sales by table, grouping by table code. Includes total orders, sales, tax, and service charges.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate     start date/time of the period (inclusive)
     * @param endDate       end date/time of the period (inclusive)
     * @param page          page number for pagination (null returns all)
     * @param size          page size for pagination (null returns all)
     * @param sortBy        field to sort by
     * @param sortDirection sort direction
     * @return TableWiseSalesReport with paginated list of tables and sales metrics
     */
    public ReportsOverviewResponse.TableWiseSalesReport getTableWiseSalesReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = orderRepository.getTableWiseSalesReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.TableWiseSalesReport.builder()
                    .tables(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Get all table IDs for batch lookup
        Set<UUID> tableIds = results.stream()
                .map(row -> (UUID) row[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Batch fetch tables
        Map<UUID, RestaurantTable> tableMap = restaurantTableRepository.findAllById(tableIds)
                .stream()
                .collect(Collectors.toMap(RestaurantTable::getId, t -> t));

        // First, map results to items with table numbers
        List<ReportsOverviewResponse.TableWiseSalesItem> itemsWithTableNumbers = results.stream()
                .map(row -> {
                    UUID tableId = (UUID) row[0];
                    Long totalOrders = ((Number) row[1]).longValue();
                    BigDecimal totalSales = BigDecimal.valueOf(((Number) row[2]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal averageOrderValue = BigDecimal.valueOf(((Number) row[3]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalTax = BigDecimal.valueOf(((Number) row[4]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalServiceCharge = BigDecimal.valueOf(((Number) row[5]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);

                    // Get table identifier - use unique tableCode as business key
                    String tableNo = "Unknown";
                    RestaurantTable table = tableMap.get(tableId);
                    if (table != null) {
                        if (table.getTableCode() != null && !table.getTableCode().isEmpty()) {
                            tableNo = table.getTableCode();
                        } else if (table.getTableOrder() != null) {
                            tableNo = String.valueOf(table.getTableOrder());
                        } else {
                            tableNo = "N/A";
                        }
                    }

                    return ReportsOverviewResponse.TableWiseSalesItem.builder()
                            .tableNo(tableNo)
                            .totalOrders(totalOrders)
                            .totalSales(totalSales)
                            .averageOrderValue(averageOrderValue)
                            .totalTax(totalTax)
                            .totalServiceCharge(totalServiceCharge)
                            .build();
                })
                .collect(Collectors.toList());

        // Group by table number and aggregate the values
        Map<String, ReportsOverviewResponse.TableWiseSalesItem> aggregatedByTableNo = new LinkedHashMap<>();
        
        for (ReportsOverviewResponse.TableWiseSalesItem item : itemsWithTableNumbers) {
            String tableNo = item.getTableNo();
            
            if (aggregatedByTableNo.containsKey(tableNo)) {
                // Aggregate existing entry
                ReportsOverviewResponse.TableWiseSalesItem existing = aggregatedByTableNo.get(tableNo);
                
                // Sum totals
                Long combinedOrders = existing.getTotalOrders() + item.getTotalOrders();
                BigDecimal combinedSales = existing.getTotalSales().add(item.getTotalSales());
                BigDecimal combinedTax = existing.getTotalTax().add(item.getTotalTax());
                BigDecimal combinedServiceCharge = existing.getTotalServiceCharge().add(item.getTotalServiceCharge());
                
                // Recalculate average order value
                BigDecimal newAverageOrderValue = combinedOrders > 0
                        ? combinedSales.divide(BigDecimal.valueOf(combinedOrders), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                
                // Update aggregated entry
                aggregatedByTableNo.put(tableNo, ReportsOverviewResponse.TableWiseSalesItem.builder()
                        .tableNo(tableNo)
                        .totalOrders(combinedOrders)
                        .totalSales(combinedSales)
                        .averageOrderValue(newAverageOrderValue)
                        .totalTax(combinedTax)
                        .totalServiceCharge(combinedServiceCharge)
                        .build());
            } else {
                // First entry for this table number
                aggregatedByTableNo.put(tableNo, item);
            }
        }

        // Convert to list
        List<ReportsOverviewResponse.TableWiseSalesItem> aggregatedTables = new ArrayList<>(aggregatedByTableNo.values());
        
        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.TableWiseSalesItem> comparator = switch (sortField) {
                case "tableno", "table_no", "tablenumber", "table_number" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTableNo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "totalorders", "total_orders" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalOrders,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalsales", "total_sales" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "averageordervalue", "average_order_value" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getAverageOrderValue,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totaltax", "total_tax" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalTax,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalservicecharge", "total_service_charge" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalServiceCharge,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalSales
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            aggregatedTables.sort(comparator);
        } else {
            // Default sorting: by totalSales descending
            aggregatedTables.sort(Comparator.comparing(
                    ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.TableWiseSalesItem> paginatedTables;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedTables = aggregatedTables;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, aggregatedTables.size());
            int toIndex = Math.min(fromIndex + pageSize, aggregatedTables.size());
            if (fromIndex >= aggregatedTables.size()) {
                paginatedTables = new ArrayList<>();
            } else {
                paginatedTables = aggregatedTables.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) aggregatedTables.size() / pageSize))
                    .totalRecords((long) aggregatedTables.size())
                    .build();
        }
        
        return ReportsOverviewResponse.TableWiseSalesReport.builder()
                .tables(paginatedTables)
                .count((long) paginatedTables.size())
                .total((long) aggregatedTables.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates discounts and promotions report for a restaurant within a date range.
     * Combines discount and promotion items, calculates usage counts and total discount amounts.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @return DiscountsPromotionsReport with paginated list of discounts/promotions and usage metrics
     */
    public ReportsOverviewResponse.DiscountsPromotionsReport getDiscountsPromotionsReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        logger.debug("Getting discounts promotions report - restaurantId: {}, startDate: {}, endDate: {}", 
                restaurantId, startDate, endDate);

        List<Object[]> results = orderRepository.getDiscountsPromotionsReport(
                restaurantId, startDate, endDate);

        logger.debug("Discounts promotions report query returned {} results", results.size());

        if (results.isEmpty()) {
            logger.debug("No discount results found for restaurant: {}", restaurantId);
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                    .discounts(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Map all results to discount items first (before sorting and pagination)
        List<ReportsOverviewResponse.DiscountPromotionItem> discounts = results.stream()
                .map(row -> {
                    String discountType = (String) row[0]; // Category: "Order", "Additional Discount", "Item", or "Category"
                    String discountCode = (String) row[1]; // Specific discount code (e.g., "SUMMER20", "VIP_OFFER")
                    String discountName = (String) row[2]; // Discount name from translation (fallback to discount code if not available)
                    Long numberOfTransactions = ((Number) row[3]).longValue();
                    BigDecimal totalDiscountApplied = BigDecimal.valueOf(((Number) row[4]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalRevenue = BigDecimal.valueOf(((Number) row[5]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalRevenueBeforeDiscount = BigDecimal.valueOf(((Number) row[6]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    // We will recompute discount efficiency in Java as:
                    // (Total Discount Applied / Total Revenue Before Discount) * 100 for each row
                    BigDecimal discountEfficiency = BigDecimal.ZERO;
                    String appliedTo = (String) row[8];

                    return ReportsOverviewResponse.DiscountPromotionItem.builder()
                            .discountType(discountType != null && !discountType.isEmpty() ? discountType : "Unknown Discount")
                            .discountCode(discountCode) // Preserve specific discount code for frontend display
                            .discountName(discountName != null && !discountName.isEmpty() ? discountName : discountCode) // Use discount name, fallback to code
                            .numberOfTransactions(numberOfTransactions)
                            .totalDiscountApplied(totalDiscountApplied)
                            .totalRevenue(totalRevenue)
                            .totalRevenueBeforeDiscount(totalRevenueBeforeDiscount)
                            .discountEfficiency(discountEfficiency)
                            .appliedTo(appliedTo != null ? appliedTo : "ORDER")
                            .build();
                })
                .collect(Collectors.toList());

        // Recalculate discount efficiency according to business standard:
        // Discount Efficiency = (Total Discount Applied / Total Revenue Before Discount) * 100
        // Formula: (discount / revenueBeforeDiscount) * 100
        discounts.forEach(item -> {
            BigDecimal rowDiscount = item.getTotalDiscountApplied() != null
                    ? item.getTotalDiscountApplied()
                    : BigDecimal.ZERO;
            BigDecimal revenueBeforeDiscount = item.getTotalRevenueBeforeDiscount() != null
                    ? item.getTotalRevenueBeforeDiscount()
                    : BigDecimal.ZERO;
            
            if (revenueBeforeDiscount.compareTo(BigDecimal.ZERO) > 0) {
                // Use higher precision for division, then round to 2 decimal places
                // Formula: (discount / revenueBeforeDiscount) * 100
                BigDecimal efficiency = rowDiscount
                        .divide(revenueBeforeDiscount, 10, RoundingMode.HALF_UP) // Use 10 decimal places for precision
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP); // Round final result to 2 decimal places
                item.setDiscountEfficiency(efficiency);
            } else {
                item.setDiscountEfficiency(BigDecimal.ZERO);
            }
        });

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.DiscountPromotionItem> comparator = switch (sortField) {
                case "discounttype", "discount_type" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getDiscountType,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "numberoftransactions", "number_of_transactions" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getNumberOfTransactions,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totaldiscountapplied", "total_discount_applied" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalrevenue", "total_revenue" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalRevenue,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalrevenuebeforediscount", "total_revenue_before_discount" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalRevenueBeforeDiscount,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "discountefficiency", "discount_efficiency" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getDiscountEfficiency,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "appliedto", "applied_to" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getAppliedTo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalDiscountApplied
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            discounts.sort(comparator);
        } else {
            // Default sorting: by totalDiscountApplied descending
            discounts.sort(Comparator.comparing(
                    ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.DiscountPromotionItem> paginatedDiscounts;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedDiscounts = discounts;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, discounts.size());
            int toIndex = Math.min(fromIndex + pageSize, discounts.size());
            if (fromIndex >= discounts.size()) {
                paginatedDiscounts = new ArrayList<>();
            } else {
                paginatedDiscounts = discounts.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) discounts.size() / pageSize))
                    .totalRecords((long) discounts.size())
                    .build();
        }
        
        return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                .discounts(paginatedDiscounts)
                .count((long) paginatedDiscounts.size())
                .total((long) discounts.size())
                .metaData(metaData)
                .build();
    }
    /**
     * Aggregates daily sales summary across multiple restaurants
     */
    public ReportsOverviewResponse.DailySalesSummary getDailySalesSummaryForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal totalSales = BigDecimal.ZERO;
        long totalOrders = 0L;
        long totalTablesServed = 0L;

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.DailySalesSummary summary = getDailySalesSummary(restaurantId, startDate, endDate);
            totalSales = totalSales.add(summary.getTotalSales());
            totalOrders += summary.getTotalOrders();
            totalTablesServed += summary.getTotalTablesServed();
        }

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ReportsOverviewResponse.DailySalesSummary.builder()
                .totalSales(totalSales)
                .totalOrders(totalOrders)
                .totalTablesServed(totalTablesServed)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    /**
     * Aggregates payment types breakdown across multiple restaurants
     */
    public List<ReportsOverviewResponse.PaymentTypeBreakdown> getPaymentTypesBreakdownForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, BigDecimal> paymentMethodTotals = new HashMap<>();
        BigDecimal[] grandTotal = {BigDecimal.ZERO}; // Use array to make it effectively final

        for (UUID restaurantId : restaurantIds) {
            List<ReportsOverviewResponse.PaymentTypeBreakdown> breakdown = getPaymentTypesBreakdown(restaurantId, startDate, endDate);
            for (ReportsOverviewResponse.PaymentTypeBreakdown item : breakdown) {
                String method = item.getPaymentMethod();
                BigDecimal amount = item.getTotalSales();
                paymentMethodTotals.merge(method, amount, BigDecimal::add);
                grandTotal[0] = grandTotal[0].add(amount);
            }
        }

        final BigDecimal finalGrandTotal = grandTotal[0];
        return paymentMethodTotals.entrySet().stream()
                .map(entry -> {
                    Double percentage = finalGrandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(finalGrandTotal, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .doubleValue()
                            : 0.0;
                    return ReportsOverviewResponse.PaymentTypeBreakdown.builder()
                            .paymentMethod(entry.getKey())
                            .totalSales(entry.getValue())
                            .percentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparing(ReportsOverviewResponse.PaymentTypeBreakdown::getTotalSales).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Aggregates itemized sales report across multiple restaurants
     */
    public ReportsOverviewResponse.ItemizedSalesReport getItemizedSalesReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection, String locale) {
        // For multi-restaurant, aggregate all items across restaurants
        Map<String, ReportsOverviewResponse.ItemizedSalesItem> itemMap = new HashMap<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.ItemizedSalesReport report = getItemizedSalesReport(
                    restaurantId, startDate, endDate, null, null, null, null, locale);
            for (ReportsOverviewResponse.ItemizedSalesItem item : report.getItems()) {
                String key = itemizedSalesMergeKey(item);
                itemMap.merge(key, item, (existing, newItem) -> {
                    Integer totalQuantity = (existing.getQuantitySold() != null ? existing.getQuantitySold() : 0) +
                            (newItem.getQuantitySold() != null ? newItem.getQuantitySold() : 0);
                    BigDecimal totalSales = existing.getTotalSales().add(newItem.getTotalSales());
                    BigDecimal unitPrice = totalQuantity > 0
                            ? totalSales.divide(BigDecimal.valueOf(totalQuantity), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ReportsOverviewResponse.ItemizedSalesItem.builder()
                            .itemCode(existing.getItemCode())
                            .itemName(existing.getItemName())
                            .category(existing.getCategory())
                            .quantitySold(totalQuantity)
                            .unitPrice(unitPrice)
                            .totalSales(totalSales)
                            .percentageOfTotalSales(existing.getPercentageOfTotalSales()) // Will be recalculated if needed
                            .build();
                });
            }
        }

        List<ReportsOverviewResponse.ItemizedSalesItem> items = new ArrayList<>(itemMap.values());
        // Apply sorting and pagination
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            // Apply sorting logic similar to single restaurant
        }
        // Apply pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        List<ReportsOverviewResponse.ItemizedSalesItem> paginatedItems;
        if (fromIndex >= items.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = items.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) items.size() / pageSize))
                .totalRecords((long) items.size())
                .build();

        return ReportsOverviewResponse.ItemizedSalesReport.builder()
                .items(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) items.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Aggregates table-wise sales report across multiple restaurants
     */
    public ReportsOverviewResponse.TableWiseSalesReport getTableWiseSalesReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {
        List<ReportsOverviewResponse.TableWiseSalesItem> allItems = new ArrayList<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.TableWiseSalesReport report = getTableWiseSalesReport(
                    restaurantId, startDate, endDate, null, null, null, null);
            if (report.getTables() != null) {
                allItems.addAll(report.getTables());
            }
        }

        // Apply sorting and pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<ReportsOverviewResponse.TableWiseSalesItem> paginatedItems;
        if (fromIndex >= allItems.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = allItems.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allItems.size() / pageSize))
                .totalRecords((long) allItems.size())
                .build();

        return ReportsOverviewResponse.TableWiseSalesReport.builder()
                .tables(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) allItems.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Aggregates discounts and promotions report across multiple restaurants
     */
    public ReportsOverviewResponse.DiscountsPromotionsReport getDiscountsPromotionsReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {
        List<ReportsOverviewResponse.DiscountPromotionItem> allItems = new ArrayList<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.DiscountsPromotionsReport report = getDiscountsPromotionsReport(
                    restaurantId, startDate, endDate, null, null, null, null);
            if (report.getDiscounts() != null) {
                allItems.addAll(report.getDiscounts());
            }
        }

        // Apply sorting and pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<ReportsOverviewResponse.DiscountPromotionItem> paginatedItems;
        if (fromIndex >= allItems.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = allItems.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allItems.size() / pageSize))
                .totalRecords((long) allItems.size())
                .build();

        return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                .discounts(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) allItems.size())
                .metaData(metaData)
                .build();
    }
}
