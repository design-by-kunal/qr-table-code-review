package com.gulfnet.shared_library.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CurrencyFormatter Tests")
class CurrencyFormatterTest {

    private static final String AMT_1234_567 = "1234.567";
    private static final String AMT_1234_57 = "1234.57";
    private static final String AMT_1234_56 = "1234.56";
    private static final String AMT_1234_50 = "1234.50";
    private static final String AMT_1000_00 = "1000.00";

    // ==================== YEN (¥) - NO DECIMALS ====================

    @Test
    @DisplayName("Yen: Format amount with decimals - should truncate to whole number")
    void testYen_WithDecimals() {
        // Input: 1234.56
        BigDecimal input = new BigDecimal(AMT_1234_56);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥");
        
        // Output: 1234 (truncate toward zero at integer scale)
        assertEquals(new BigDecimal("1234"), result);
        assertEquals(0, result.scale());
    }

    @Test
    @DisplayName("Yen: Format amount - rounding down")
    void testYen_RoundingDown() {
        // Input: 1234.49
        BigDecimal input = new BigDecimal("1234.49");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥");
        
        // Output: 1234
        assertEquals(new BigDecimal("1234"), result);
    }

    @Test
    @DisplayName("Yen: Format amount - exact half truncates (default policy)")
    void testYen_RoundingUp() {
        // Input: 1234.50
        BigDecimal input = new BigDecimal(AMT_1234_50);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥");
        
        // Output: 1234 (truncate; not half-up to 1235)
        assertEquals(new BigDecimal("1234"), result);
    }

    @Test
    @DisplayName("Yen: Format whole number - no change")
    void testYen_WholeNumber() {
        // Input: 1000.00
        BigDecimal input = new BigDecimal(AMT_1000_00);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥");
        
        // Output: 1000
        assertEquals(new BigDecimal("1000"), result);
        assertEquals(0, result.scale());
    }

    @Test
    @DisplayName("Yen: Format with Unicode symbol")
    void testYen_UnicodeSymbol() {
        // Input: 1234.56
        BigDecimal input = new BigDecimal(AMT_1234_56);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "\u00A5");
        
        // Output: 1234 (truncate)
        assertEquals(new BigDecimal("1234"), result);
        assertEquals(0, result.scale());
    }

    // ==================== DOLLAR ($) - 2 DECIMALS ====================

    @Test
    @DisplayName("Dollar: Format amount with 3 decimals - should truncate to 2 decimals")
    void testDollar_WithThreeDecimals() {
        // Input: 1234.567
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 1234.56 (truncate to 2 decimals)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Dollar: Format amount - rounding down")
    void testDollar_RoundingDown() {
        // Input: 1234.564
        BigDecimal input = new BigDecimal("1234.564");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 1234.56
        assertEquals(new BigDecimal(AMT_1234_56), result);
    }

    @Test
    @DisplayName("Dollar: Format amount - rounding up")
    void testDollar_RoundingUp() {
        // Input: 1234.565
        BigDecimal input = new BigDecimal("1234.565");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 1234.56 (truncate; 1234.565 does not round half-up)
        assertEquals(new BigDecimal(AMT_1234_56), result);
    }

    @Test
    @DisplayName("Dollar: Format amount with 2 decimals - no change")
    void testDollar_TwoDecimals() {
        // Input: 1234.50
        BigDecimal input = new BigDecimal(AMT_1234_50);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 1234.50
        assertEquals(new BigDecimal(AMT_1234_50), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Dollar: Format whole number - adds 2 decimals")
    void testDollar_WholeNumber() {
        // Input: 1000
        BigDecimal input = new BigDecimal("1000");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 1000.00
        assertEquals(new BigDecimal(AMT_1000_00), result);
        assertEquals(2, result.scale());
    }

    // ==================== THAI BAHT (฿) - 2 DECIMALS ====================

    @Test
    @DisplayName("Thai Baht: Format amount with 3 decimals - should truncate to 2 decimals")
    void testThaiBaht_WithThreeDecimals() {
        // Input: 1234.567
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "฿");
        
        // Output: 1234.56 (truncate to 2 decimals)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Thai Baht: Format amount - rounding down")
    void testThaiBaht_RoundingDown() {
        // Input: 1234.564
        BigDecimal input = new BigDecimal("1234.564");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "฿");
        
        // Output: 1234.56
        assertEquals(new BigDecimal(AMT_1234_56), result);
    }

    @Test
    @DisplayName("Thai Baht: Format amount - rounding up")
    void testThaiBaht_RoundingUp() {
        // Input: 1234.565
        BigDecimal input = new BigDecimal("1234.565");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "฿");
        
        // Output: 1234.56 (truncate)
        assertEquals(new BigDecimal(AMT_1234_56), result);
    }

    @Test
    @DisplayName("Thai Baht: Format whole number - adds 2 decimals")
    void testThaiBaht_WholeNumber() {
        // Input: 1000
        BigDecimal input = new BigDecimal("1000");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "฿");
        
        // Output: 1000.00
        assertEquals(new BigDecimal(AMT_1000_00), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Thai Baht: Format with Unicode symbol")
    void testThaiBaht_UnicodeSymbol() {
        // Input: 1234.567
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "\u0E3F");
        
        // Output: 1234.56 (truncate)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Null amount - should return zero")
    void testNullAmount() {
        BigDecimal result = CurrencyFormatter.formatAmount(null, "¥");
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("Null currency - should default to 2 decimals")
    void testNullCurrency() {
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, null);
        
        // Output: 1234.56 (defaults to 2 decimals, truncate)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Empty currency - should default to 2 decimals")
    void testEmptyCurrency() {
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "");
        
        // Output: 1234.56 (defaults to 2 decimals, truncate)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Unknown currency - should default to 2 decimals")
    void testUnknownCurrency() {
        BigDecimal input = new BigDecimal(AMT_1234_567);
        BigDecimal result = CurrencyFormatter.formatAmount(input, "€");
        
        // Output: 1234.56 (defaults to 2 decimals, truncate)
        assertEquals(new BigDecimal(AMT_1234_56), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Zero amount - Yen")
    void testZeroAmount_Yen() {
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥");
        
        // Output: 0
        assertEquals(BigDecimal.ZERO, result);
        assertEquals(0, result.scale());
    }

    @Test
    @DisplayName("Zero amount - Dollar")
    void testZeroAmount_Dollar() {
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal result = CurrencyFormatter.formatAmount(input, "$");
        
        // Output: 0.00
        assertEquals(new BigDecimal("0.00"), result);
        assertEquals(2, result.scale());
    }

    @Test
    @DisplayName("Zero amount - Thai Baht")
    void testZeroAmount_ThaiBaht() {
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal result = CurrencyFormatter.formatAmount(input, "฿");
        
        // Output: 0.00
        assertEquals(new BigDecimal("0.00"), result);
        assertEquals(2, result.scale());
    }

    // ==================== UTILITY METHODS ====================

    @Test
    @DisplayName("Get decimal places - Yen should return 0")
    void testGetDecimalPlaces_Yen() {
        assertEquals(0, CurrencyFormatter.getDecimalPlaces("¥"));
    }

    @Test
    @DisplayName("Get decimal places - Dollar should return 2")
    void testGetDecimalPlaces_Dollar() {
        assertEquals(2, CurrencyFormatter.getDecimalPlaces("$"));
    }

    @Test
    @DisplayName("Get decimal places - Thai Baht should return 2")
    void testGetDecimalPlaces_ThaiBaht() {
        assertEquals(2, CurrencyFormatter.getDecimalPlaces("฿"));
    }

    @Test
    @DisplayName("Excel monetary format pattern follows decimal places")
    void testMonetaryExcelDataFormatPattern() {
        assertEquals("#,##0", CurrencyFormatter.getMonetaryExcelDataFormatPattern("¥"));
        assertEquals("#,##0.00", CurrencyFormatter.getMonetaryExcelDataFormatPattern("$"));
        assertEquals("#,##0.00", CurrencyFormatter.getMonetaryExcelDataFormatPattern(null));
    }

    @Test
    @DisplayName("Is Yen - should return true for Yen symbol")
    void testIsYen() {
        assertTrue(CurrencyFormatter.isYen("¥"));
        assertTrue(CurrencyFormatter.isYen("\u00A5"));
        assertFalse(CurrencyFormatter.isYen("$"));
        assertFalse(CurrencyFormatter.isYen("฿"));
    }

    @Test
    @DisplayName("Is Dollar - should return true for Dollar symbol")
    void testIsDollar() {
        assertTrue(CurrencyFormatter.isDollar("$"));
        assertFalse(CurrencyFormatter.isDollar("¥"));
        assertFalse(CurrencyFormatter.isDollar("฿"));
    }

    @Test
    @DisplayName("Is Thai Baht - should return true for Thai Baht symbol")
    void testIsThaiBaht() {
        assertTrue(CurrencyFormatter.isThaiBaht("฿"));
        assertTrue(CurrencyFormatter.isThaiBaht("\u0E3F"));
        assertFalse(CurrencyFormatter.isThaiBaht("¥"));
        assertFalse(CurrencyFormatter.isThaiBaht("$"));
    }

    // ==================== REAL-WORLD CALCULATION SCENARIOS ====================

    @Test
    @DisplayName("Order calculation: Subtotal with Yen")
    void testOrderCalculation_SubtotalYen() {
        // Example: Subtotal calculation result
        BigDecimal subtotal = new BigDecimal("1250.75");
        BigDecimal formatted = CurrencyFormatter.formatAmount(subtotal, "¥");
        
        // Output: 1250 (truncate at integer scale)
        assertEquals(new BigDecimal("1250"), formatted);
    }

    @Test
    @DisplayName("Order calculation: Tax amount with Dollar")
    void testOrderCalculation_TaxDollar() {
        // Example: Tax calculation result
        BigDecimal tax = new BigDecimal("62.5375");
        BigDecimal formatted = CurrencyFormatter.formatAmount(tax, "$");
        
        // Output: 62.53 (truncate to 2 decimals)
        assertEquals(new BigDecimal("62.53"), formatted);
    }

    @Test
    @DisplayName("Order calculation: Total with Thai Baht")
    void testOrderCalculation_TotalThaiBaht() {
        // Example: Total calculation result
        BigDecimal total = new BigDecimal("1313.125");
        BigDecimal formatted = CurrencyFormatter.formatAmount(total, "฿");
        
        // Output: 1313.12 (truncate to 2 decimals)
        assertEquals(new BigDecimal("1313.12"), formatted);
    }

    /**
     * Ensures {@link CurrencyFormatter#formatAmount(java.math.BigDecimal, String)} applies the correct
     * rounding and scale for the same monetary amount when formatted as yen (integer), US dollar, and Thai baht.
     */
    @Test
    @DisplayName("Order calculation: Multiple amounts with different currencies")
    void testOrderCalculation_MultipleCurrencies() {
        BigDecimal amount = new BigDecimal(AMT_1234_567);
        
        BigDecimal yenResult = CurrencyFormatter.formatAmount(amount, "¥");
        BigDecimal dollarResult = CurrencyFormatter.formatAmount(amount, "$");
        BigDecimal bahtResult = CurrencyFormatter.formatAmount(amount, "฿");
        
        // Yen: 1234 (no decimals, truncate)
        assertEquals(new BigDecimal("1234"), yenResult);
        assertEquals(0, yenResult.scale());
        
        // Dollar: 1234.56 (2 decimals)
        assertEquals(new BigDecimal(AMT_1234_56), dollarResult);
        assertEquals(2, dollarResult.scale());
        
        // Thai Baht: 1234.56 (2 decimals)
        assertEquals(new BigDecimal(AMT_1234_56), bahtResult);
        assertEquals(2, bahtResult.scale());
    }

    @Test
    @DisplayName("CSV monetary string: follows formatAmount scale per currency")
    void testCsvMonetaryString_WholeUnitsAfterCurrencyRules() {
        assertEquals("1234.55", CurrencyFormatter.formatCsvMonetaryString(new BigDecimal("1234.556"), "$"));
        assertEquals("1234", CurrencyFormatter.formatCsvMonetaryString(new BigDecimal(AMT_1234_56), "¥"));
        assertEquals("0", CurrencyFormatter.formatCsvMonetaryString(null, "$"));
    }

    @Test
    @DisplayName("CSV percent string: no fractional digits")
    void testCsvPercentString_WholeUnits() {
        assertEquals("33", CurrencyFormatter.formatCsvPercentString(33.4));
        assertEquals("34", CurrencyFormatter.formatCsvPercentString(33.6));
        assertEquals("0", CurrencyFormatter.formatCsvPercentString(null));
    }

    // ==================== ROUNDING MODE OVERRIDE ====================

    @Test
    @DisplayName("Yen: ROUND_DOWN (truncate) should drop fractional part")
    void testYen_RoundDown_Truncate() {
        BigDecimal input = new BigDecimal("10.50");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥", RoundingMode.DOWN);
        assertEquals(new BigDecimal("10"), result);
        assertEquals(0, result.scale());
    }

    @Test
    @DisplayName("Yen: ROUND_UP should always round up when fractional exists")
    void testYen_RoundUp_Ceiling() {
        BigDecimal input = new BigDecimal("10.01");
        BigDecimal result = CurrencyFormatter.formatAmount(input, "¥", RoundingMode.CEILING);
        assertEquals(new BigDecimal("11"), result);
        assertEquals(0, result.scale());
    }
}
