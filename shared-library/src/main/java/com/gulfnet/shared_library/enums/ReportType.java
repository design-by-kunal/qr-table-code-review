package com.gulfnet.shared_library.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing different report types available in the system.
 * Each report type corresponds to a specific data export functionality.
 */
@Getter
@RequiredArgsConstructor
public enum ReportType {
    DASHBOARD_STATISTICS("dashboard_statistics", "Dashboard Statistics Report"),
    DISCOUNTS_OFFERS("discounts_offers", "Discounts and Offers Applied Report"),
    DISCOUNTS_PROMOTIONS("discounts_promotions", "Discounts and Promotions Report"),
    ITEMIZED_SALES("itemized_sales", "Itemized Sales Report"),
    TABLE_WISE_SALES("table_wise_sales", "Table-wise Sales Report"),
    PAYMENT_TYPES_BREAKDOWN("payment_types_breakdown", "Payment Types Breakdown Report"),
    DAILY_SALES_SUMMARY("daily_sales_summary", "Daily Sales Summary Report"),
    TRANSACTIONS("transactions", "Transactions Report"),
    ORDERS("orders", "Orders Report"),
    SALES("sales", "Sales Report"),
    SALES_AND_REVENUE("sales_and_revenue", "Sales and Revenue Report"),
    PAYMENT_AND_FINANCIAL("payment_and_financial", "Payment and Financial Report"),
    MENU_PERFORMANCE("menu_performance", "Menu Performance Report"),
    EMPLOYEE_PERFORMANCE("employee_performance", "Employee Performance Report"),
    STAFF_PERFORMANCE("staff_performance", "Staff Performance Report");

    private final String code;
    private final String displayName;

    /**
     * Get ReportType by code (case-insensitive)
     */
    public static ReportType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Report type code cannot be null or blank");
        }
        for (ReportType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown report type code: " + code);
    }
}
