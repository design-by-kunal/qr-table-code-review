package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.util.AddressDto;
import com.gulfnet.shared_library.util.AddressFormatter;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.exception.RefundReceiptException;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.util.ReceiptUtil;
import com.gulfnet.shared_library.util.DateTimeUtil;
import com.gulfnet.shared_library.util.NameUtil;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Service for generating refund receipt PDFs optimized for thermal printers.
 * 
 * Supports standard thermal printer sizes:
 * - 72mm width (most common)
 * - 80mm width (also common)
 * 
 * Page size is configurable via chain configuration.
 * Receipts are optimized for narrow thermal printer paper with responsive design.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundReceiptService {

    // HTML template constants
    private static final String HTML_DIVIDER = "<div class='divider'></div>";
    private static final String HTML_OPEN_ADDRESS_DIV = "<div class='address'>";
    private static final String HTML_EMPTY_QTY_CELL = "<td class='item-qty'></td>";
    private static final String HTML_EMPTY_PRICE_CELL = "<td class='item-price'></td>";
    private static final String HTML_CLOSE_DIV = "</div>";
    private static final String HTML_CLOSE_TD = "</td>";
    private static final String HTML_CLOSE_TR = "</tr>";
    private static final String HTML_CLOSE_TH = "</th>";
    private static final String HTML_OPEN_DIV_STRONG = "<div><strong>";
    private static final String HTML_CLOSE_STRONG_SPACE = ":</strong> ";

    private static final String MSG_RECEIPT_ALCOHOLIC_TAXABLE_AMOUNT = "receipt.alcoholic.taxable.amount";
    private static final String MSG_RECEIPT_ALCOHOLIC_ITEM_TAX = "receipt.alcoholic.item.tax";
    private static final String MSG_RECEIPT_NON_ALCOHOLIC_TAXABLE_AMOUNT = "receipt.non.alcoholic.taxable.amount";
    private static final String MSG_RECEIPT_NON_ALCOHOLIC_ITEM_TAX = "receipt.non.alcoholic.item.tax";

    private final AWSService awsService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final MessageUtil messageUtil;
    private final ReceiptUtil receiptUtil;

    /**
     * Generates a refund receipt PDF optimized for thermal printers (72mm or 80mm width) and uploads to S3
     * 
     * @param refund The refund entity
     * @param transaction The original transaction
     * @param order The original order
     * @param restaurant The restaurant
     * @param refundItems List of refund items
     * @return S3 URL of the generated PDF receipt
     */
    public String generateRefundReceiptPdf(Refund refund, Transaction transaction, Order order,
                                          Restaurant restaurant, List<RefundItem> refundItems) {
        return generateRefundReceiptPdf(refund, transaction, order, restaurant, refundItems, null);
    }

    /**
     * @param languageOverride used only when chain {@code defaultLanguageCode} is not set (then request locale / en)
     */
    public String generateRefundReceiptPdf(Refund refund, Transaction transaction, Order order,
                                          Restaurant restaurant, List<RefundItem> refundItems, String languageOverride) {
        try {
            log.info("Refund receipt generation START - refundId={}, refundNumber={}, transactionId={}, orderId={}",
                    refund != null ? refund.getId() : null,
                    refund != null ? refund.getRefundNumber() : null,
                    transaction != null ? transaction.getId() : null,
                    order != null ? order.getId() : null);

            Refund nonNullRefund = Objects.requireNonNull(refund, "refund");
            Transaction nonNullTransaction = Objects.requireNonNull(transaction, "transaction");
            Order nonNullOrder = Objects.requireNonNull(order, "order");
            Restaurant nonNullRestaurant = Objects.requireNonNull(restaurant, "restaurant");

            Locale targetLocale = resolveTargetLocale(languageOverride);
            log.info("Refund receipt generation - resolved targetLocale='{}' for refundId={}, orderId={}",
                    targetLocale.getLanguage(),
                    refund != null ? refund.getId() : null,
                    order != null ? order.getId() : null);

            log.info("Refund receipt generation - building HTML for refundId={}, orderId={}",
                    nonNullRefund.getId(),
                    nonNullOrder.getId());
            String htmlContent = generateRefundReceiptHtml(nonNullRefund, nonNullTransaction, nonNullOrder, nonNullRestaurant, refundItems, targetLocale);
            log.info("Refund receipt generation - HTML built successfully for refundId={}, orderId={}, htmlLength={}",
                    nonNullRefund.getId(),
                    nonNullOrder.getId(),
                    htmlContent != null ? htmlContent.length() : 0);

            // Get receipt page size from chain configuration
            ReceiptPageConfig pageConfig = resolveReceiptPageConfig();
            log.info("Refund receipt generation - using page configuration refundId={}, orderId={}, widthMm={}, maxHeightMm={}",
                    nonNullRefund.getId(),
                    nonNullOrder.getId(),
                    pageConfig.widthMm(),
                    pageConfig.maxHeightMm());

            byte[] pdfBytes = convertHtmlToPdfBytes(htmlContent, pageConfig, nonNullRefund.getId(), nonNullOrder.getId());
            log.info("Refund receipt generation - HTML to PDF conversion completed for refundId={}, orderId={}, pdfSizeBytes={}",
                    nonNullRefund.getId(),
                    nonNullOrder.getId(),
                    pdfBytes.length);
            InputStream inputStream = new ByteArrayInputStream(pdfBytes);
            String fileName = "refund_receipt_" + nonNullRefund.getRefundNumber() + ".pdf";
            String s3Key = "refunds/" + nonNullRefund.getId().toString() + "/" + targetLocale.getLanguage().toLowerCase() + "/" + fileName;
            
            // Upload PDF with Content-Type header for proper browser preview
            log.info("Refund receipt generation - uploading PDF to S3 for refundId={}, orderId={}, s3Key={}",
                    nonNullRefund.getId(),
                    nonNullOrder.getId(),
                    s3Key);
            String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, pdfBytes.length, "application/pdf");
            log.info("Refund receipt PDF (locale={}, width={}mm) uploaded to S3 - URL: {}", 
                    targetLocale.getLanguage(), pageConfig.widthMm(), uploadedFileUrl);

            log.info("Refund receipt generation SUCCESS - refundId={}, refundNumber={}, orderId={}, receiptUrl={}",
                    nonNullRefund.getId(),
                    nonNullRefund.getRefundNumber(),
                    nonNullOrder.getId(),
                    uploadedFileUrl);
            return uploadedFileUrl;
            
        } catch (Exception e) {
            log.error("Refund receipt generation FAILED - refundId={}, orderId={}, error={}",
                    refund != null ? refund.getId() : null,
                    order != null ? order.getId() : null,
                    e.getMessage(), e);
            throw new RefundReceiptException("Failed to generate refund receipt PDF", e);
        }
    }

    private Locale resolveTargetLocale(String languageOverride) {
        String chainDefault = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getDefaultLanguageCode()
                : null;
        String resolvedLang = DateTimeUtil.resolveReceiptDisplayLanguage(chainDefault, languageOverride);
        return Locale.forLanguageTag(resolvedLang);
    }

    private record ReceiptPageConfig(int widthMm, int maxHeightMm) {
    }

    private ReceiptPageConfig resolveReceiptPageConfig() {
        int receiptWidthMm = 72; // Default to 72mm
        int receiptMaxHeightMm = 0; // 0 means unlimited/continuous paper

        RestaurantChainConfigProperties.ReceiptPageSize pageSize = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getReceiptPageSize()
                : null;

        if (pageSize != null && pageSize.getWidthMm() > 0) {
            receiptWidthMm = pageSize.getWidthMm();
            receiptMaxHeightMm = pageSize.getMaxHeightMm() > 0 ? pageSize.getMaxHeightMm() : 0;
        }

        return new ReceiptPageConfig(receiptWidthMm, receiptMaxHeightMm);
    }

    private byte[] convertHtmlToPdfBytes(String htmlContent, ReceiptPageConfig pageConfig, Object refundId, Object orderId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Convert mm to points (1mm = 2.83465 points)
        float widthPoints = pageConfig.widthMm() * 2.83465f;
        float heightPoints = pageConfig.maxHeightMm() > 0 ? pageConfig.maxHeightMm() * 2.83465f : 600f;
        Rectangle pageSizeRect = new Rectangle(widthPoints, heightPoints);

        com.itextpdf.html2pdf.ConverterProperties properties = new com.itextpdf.html2pdf.ConverterProperties();
        properties.setCharset("UTF-8");
        properties.setFontProvider(buildFontProvider());

        log.info("Refund receipt generation - starting HTML to PDF conversion for refundId={}, orderId={}", refundId, orderId);
        com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        pdfDoc.setDefaultPageSize(new PageSize(pageSizeRect));
        HtmlConverter.convertToPdf(htmlContent, pdfDoc, properties);
        pdfDoc.close();

        return baos.toByteArray();
    }

    private com.itextpdf.html2pdf.resolver.font.DefaultFontProvider buildFontProvider() {
        com.itextpdf.html2pdf.resolver.font.DefaultFontProvider fontProvider =
                new com.itextpdf.html2pdf.resolver.font.DefaultFontProvider(false, false, false);
        fontProvider.addStandardPdfFonts();
        fontProvider.addSystemFonts();
        registerBundledJapaneseFontBestEffort(fontProvider);
        return fontProvider;
    }

    /**
     * Generates HTML content for refund receipt optimized for thermal printers.
     * Optimized for narrow width (72mm/80mm) with responsive design.
     */
    private String generateRefundReceiptHtml(Refund refund, Transaction transaction, Order order, 
                                            Restaurant restaurant, List<RefundItem> refundItems, Locale locale) {
        StringBuilder html = new StringBuilder();
        Locale userLocale = locale != null ? locale : Locale.ENGLISH;
        
        // Extract chain object once for readability and efficiency
        var chain = restaurantChainConfigProperties.getChain();
        String chainCurrency = chain != null ? chain.getCurrency() : null;
        String currencySymbol = chainCurrency != null ? 
                               chainCurrency : 
                               messageUtil.getMessage("receipt.currency.symbol", userLocale);
        
        // Get receipt width for responsive design
        int receiptWidthMm = 72; // Default
        RestaurantChainConfigProperties.ReceiptPageSize pageSize = chain != null ? chain.getReceiptPageSize() : null;
        if (pageSize != null && pageSize.getWidthMm() > 0) {
            receiptWidthMm = pageSize.getWidthMm();
        }
        
        // Set the locale in LocaleContextHolder for MessageUtil to use
        Locale originalLocale = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(userLocale);
        
        try {
            appendHtmlHead(html, receiptWidthMm);
            appendReceiptStyles(html, receiptWidthMm);
            html.append("</style>");
            html.append("</head><body>");
            
            appendReceiptHeader(html, restaurant, userLocale);
            html.append(HTML_DIVIDER);
            appendRefundInfo(html, refund, transaction, order, userLocale);
            html.append(HTML_DIVIDER);
            
            // Determine tax comparison and marker usage
            TaxDisplayConfig taxConfig = determineTaxDisplayConfig(order, chain);
            
            // Check if there are any alcoholic items in the refund
            boolean hasAlcoholicItems = checkForAlcoholicItems(refundItems);
            
            appendRefundedItems(html, refundItems, currencySymbol, userLocale, taxConfig, hasAlcoholicItems);
            html.append(HTML_DIVIDER);
            appendRefundTotals(html, refund, currencySymbol, userLocale, chain, taxConfig);
            html.append(HTML_DIVIDER);
            appendReceiptFooter(html, userLocale);
            
            html.append("</body></html>");
            
            return html.toString();
            
        } finally {
            // Restore original locale
            LocaleContextHolder.setLocale(originalLocale);
        }
    }

    // ==================== HTML GENERATION HELPERS ====================

    /**
     * Configuration for tax display logic
     */
    private static class TaxDisplayConfig {
        final boolean useAlcoholMarker;
        final RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge;
        final RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge;
        
        TaxDisplayConfig(boolean useAlcoholMarker,
                        RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge,
                        RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge) {
            this.useAlcoholMarker = useAlcoholMarker;
            this.alcoholicTaxCharge = alcoholicTaxCharge;
            this.nonAlcoholicTaxCharge = nonAlcoholicTaxCharge;
        }
    }

    /**
     * Determines tax display configuration based on alcoholic vs non-alcoholic tax rates
     */
    private TaxDisplayConfig determineTaxDisplayConfig(Order order, RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (chain == null || chain.getTaxSetup() == null || order.getOrderType() == null) {
            return new TaxDisplayConfig(false, null, null);
        }
        
        RestaurantChainConfigProperties.TaxSetup taxSetup = chain.getTaxSetup();
        RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge;
        RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge;
        
        if (order.getOrderType() == OrderType.DINE_IN) {
            if (taxSetup.getDineIn() == null) {
                return new TaxDisplayConfig(false, null, null);
            }
            alcoholicTaxCharge = taxSetup.getDineIn().getAlcoholic();
            nonAlcoholicTaxCharge = taxSetup.getDineIn().getNonAlcoholic();
        } else {
            if (taxSetup.getTakeAway() == null) {
                return new TaxDisplayConfig(false, null, null);
            }
            alcoholicTaxCharge = taxSetup.getTakeAway().getAlcoholic();
            nonAlcoholicTaxCharge = taxSetup.getTakeAway().getNonAlcoholic();
        }
        
        if (alcoholicTaxCharge == null || nonAlcoholicTaxCharge == null) {
            return new TaxDisplayConfig(false, alcoholicTaxCharge, nonAlcoholicTaxCharge);
        }

        // Show alcohol marker whenever tax setup exists (even if rates are the same)
        // This helps identify alcoholic items on receipts
        boolean useMarker = true; // Always show marker when tax setup is configured
        
        return new TaxDisplayConfig(useMarker, alcoholicTaxCharge, nonAlcoholicTaxCharge);
    }

    /**
     * Builds a charge label with type and value (for service charge, packing charge, tax)
     */
    private String buildChargeLabel(String baseLabel, double value, ChargeType type, String currencySymbol, Locale userLocale) {
        if (type == ChargeType.PERCENT) {
            // Round to integer if close to whole number, otherwise show decimal
            int intValue = (int) Math.round(value);
            if (Math.abs(value - intValue) < 0.01) {
                return baseLabel + " (" + intValue + "%)";
            } else {
                return baseLabel + " (" + CurrencyFormatter.formatAmount(BigDecimal.valueOf(value), currencySymbol) + "%)";
            }
        } else {
            // FLAT type
            String flatLabel = messageUtil.getMessage("receipt.charge.flat", userLocale, "Flat");
            return baseLabel + " (" + flatLabel + " " + currencySymbol + CurrencyFormatter.formatAmount(BigDecimal.valueOf(value), currencySymbol) + ")";
        }
    }

    /**
     * Resolves taxable base for consumption tax display.
     *
     * New refunds should already persist `*TaxableRefundAmount` in DB.
     * For older records (null taxable fields), infer it from the tax amount when configured
     * tax type is percentage-based.
     */
    private BigDecimal resolveTaxableRefundAmount(BigDecimal storedTaxableRefundAmount,
                                                   BigDecimal taxRefundAmount,
                                                   RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge) {
        if (storedTaxableRefundAmount != null) {
            return storedTaxableRefundAmount;
        }
        if (taxRefundAmount == null || taxCharge == null) {
            return null;
        }
        if (taxCharge.getType() == ChargeType.PERCENT && taxCharge.getValue() != 0) {
            // taxRefundAmount = taxableBase * (rate / 100)  => taxableBase = taxRefundAmount * 100 / rate
            return taxRefundAmount.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(taxCharge.getValue()), 10, RoundingMode.HALF_UP);
        }
        // FLAT taxes don't have a derivable taxable base
        return null;
    }

    /**
     * Checks if an item is alcoholic
     */
    private boolean isAlcoholicItem(OrderedItem item) {
        return item.getAlcoholType() != null && item.getAlcoholType() == AlcoholType.ALCOHOLIC;
    }

    /**
     * Checks if there are any alcoholic items in the refund items (including combo items)
     *
     * @param refundItems list of refund items
     * @return true if any alcoholic items are found, false otherwise
     */
    private boolean checkForAlcoholicItems(List<RefundItem> refundItems) {
        for (RefundItem refundItem : refundItems) {
            OrderedItem orderedItem = refundItem.getOrderedItem();
            OrderedCombo orderedCombo = refundItem.getOrderedCombo();
            
            if (orderedItem != null && isAlcoholicItem(orderedItem)) {
                return true;
            }
            
            if (orderedCombo != null && orderedCombo.getOrderedItems() != null) {
                for (OrderedItem ci : orderedCombo.getOrderedItems()) {
                    if (isAlcoholicItem(ci)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Appends the HTML document head section including DOCTYPE, meta tags, and opening style tag.
     *
     * @param html          the StringBuilder to append HTML head to
     * @param receiptWidthMm the receipt width in millimeters for viewport meta tag
     */
    private void appendHtmlHead(StringBuilder html, int receiptWidthMm) {
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
        html.append("<meta name='viewport' content='width=").append(receiptWidthMm).append("mm, initial-scale=1.0'>");
        html.append("<style>");
    }

    /**
     * Appends CSS styles to the receipt HTML for thermal printer formatting.
     *
     * @param html          the StringBuilder to append styles to
     * @param receiptWidthMm the receipt width in millimeters
     */
    private void appendReceiptStyles(StringBuilder html, int receiptWidthMm) {
        // Base styles optimized for thermal printer (narrow width)
        html.append("@page { size: ").append(receiptWidthMm).append("mm auto; margin: 0.5mm; }");
        html.append("body { ");
        // Prefer bundled Japanese font, then Noto Sans JP, then fall back to system sans-serif
        html.append("font-family: 'NotoSansCJKjp-Regular', 'Noto Sans JP', Arial, 'Helvetica Neue', Helvetica, sans-serif; ");
        html.append("font-size: 11px; ");
        html.append("margin: 0; ");
        html.append("padding: 0.5mm; ");
        html.append("width: ").append(receiptWidthMm - 1).append("mm; ");
        html.append("max-width: 100%; ");
        html.append("line-height: 1.3; ");
        html.append("}");
        
        
        html.append(".header { display: flex; align-items: stretch; gap: 1px; margin-bottom: 0; padding-bottom: 0; }");
        html.append(".header-logo { width: 58px; height: 58px; flex: 0 0 58px; display: flex; align-items: center; justify-content: center; overflow: hidden; }");
        html.append(".logo { width: 100%; height: 100%; margin: 0; display: block; object-fit: cover; }");
        html.append(".header-details { flex: 1; min-width: 0; text-align: left; margin-left: 18px;}");
        html.append(".restaurant-name { font-size: 13px; font-weight: bold; margin: 0 0 1px 0; text-transform: uppercase; letter-spacing: 0.25px; word-break: break-word;}");
        html.append(".address { font-size: 9px; margin: 0 0 1px 0; white-space: pre-line; word-break: break-word; }");
        html.append(".receipt-title { font-size: 12px; font-weight: bold; margin: 0; text-transform: uppercase; letter-spacing: 1px; }");
        
        // Refund info styles
        html.append(".refund-info { margin-bottom: 6px; font-size: 10px; }");
        html.append(".refund-info div { margin: 2px 0; }");
        html.append(".refund-info strong { font-weight: bold; }");
        
        // Items table styles
        html.append(".items-table { width: 100%; border-collapse: collapse; margin-bottom: 2px; font-size: 10px; }");
        html.append(".items-table th, .items-table td { padding: 2px 1px; text-align: left; }");
        html.append(".items-table th { font-weight: bold; font-size: 9px; text-transform: uppercase; letter-spacing: 0.5px; padding-bottom: 2px; }");
        html.append(".items-table .item-name { word-break: break-word; max-width: 55%; }");
        html.append(".items-table .item-qty { text-align: center; width: 15%; }");
        html.append(".items-table .item-price { text-align: right; width: 30%; }");
        html.append(".items-table .item-discount { font-size: 9px; color: #000; padding-left: 12px; }");
        html.append(".items-table .item-mod { font-size: 9px; color: #000; padding-left: 12px; }");
        html.append(".items-table .item-sub { font-size: 9px; color: #000; padding-left: 8px; }");
        
        // Totals styles
        html.append(".totals { margin-top: 0; padding-top: 0; }");
        html.append(".total-line { display: flex; justify-content: space-between; align-items: center; margin: 2px 0; font-size: 10px; }");
        html.append(".total-line.total-final { font-weight: bold; font-size: 12px; border-top: 1px solid #000; padding-top: 3px; margin-top: 4px; }");
        html.append(".total-label { text-align: left; flex: 1; }");
        html.append(".total-value { text-align: right; white-space: nowrap; }");
        
        // Payment info styles
        html.append(".payment-info { margin-top: 6px; font-size: 10px; }");
        html.append(".payment-info div { display: flex; justify-content: space-between; align-items: center; margin: 2px 0; }");
        html.append(".payment-info strong { font-weight: bold; }");
        html.append(".payment-label { text-align: left; flex: 1; }");
        html.append(".payment-value { text-align: right; white-space: nowrap; }");
        
        // Footer styles
        html.append(".footer { text-align: center; margin-top: 7px; padding-top: 3px; }");
        html.append(".thank-you { font-weight: bold; font-size: 11px; margin-bottom: 2px; }");
        
        // Divider style
        html.append(".divider { border-top: 1px solid #000; margin: 2px 0; }");
        
        // Responsive adjustments
        html.append("@media print { ");
        html.append("body { width: ").append(receiptWidthMm - 1).append("mm; } ");
        html.append("}");
    }

    /**
     * Appends the receipt header section including restaurant logo and name.
     *
     * @param html        the StringBuilder to append header to
     * @param restaurant  the restaurant entity
     * @param userLocale  locale for localized text
     */
    private void appendReceiptHeader(StringBuilder html, Restaurant restaurant, Locale userLocale) {
        html.append("<div class='header'>");
        if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isBlank()) {
            html.append("<div class='header-logo'>");
            appendRestaurantLogo(html, restaurant);
            html.append(HTML_CLOSE_DIV);
        }
        html.append("<div class='header-details'>");
        html.append("<div class='restaurant-name'>").append(receiptUtil.escapeHtml(receiptUtil.getRestaurantName(restaurant, userLocale))).append(HTML_CLOSE_DIV);
        AddressDto addressDto = AddressDto.fromRestaurant(restaurant, chainCountryName());
        String formattedAddress = AddressFormatter.format(addressDto, userLocale);
        if (!formattedAddress.isEmpty()) {
            html.append(HTML_OPEN_ADDRESS_DIV).append(receiptUtil.escapeHtml(formattedAddress)).append(HTML_CLOSE_DIV);
        }
        if (restaurant.getPhoneNumber() != null && !restaurant.getPhoneNumber().isBlank()) {
            String phoneLabel = messageUtil.getMessage("receipt.phone", userLocale);
            html.append(HTML_OPEN_ADDRESS_DIV).append("<strong>").append(phoneLabel).append(HTML_CLOSE_STRONG_SPACE)
                    .append(receiptUtil.escapeHtml(restaurant.getPhoneNumber().trim())).append(HTML_CLOSE_DIV);
        }
        if (restaurant.getGstNumber() != null && !restaurant.getGstNumber().isBlank()) {
            String gstLabel = messageUtil.getMessage("receipt.gst.number", userLocale);
            html.append(HTML_OPEN_ADDRESS_DIV).append("<strong>").append(gstLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(restaurant.getGstNumber())).append(HTML_CLOSE_DIV);
        }
        // Localized refund receipt title
        String refundTitle = messageUtil.getMessage("receipt.refund.title", userLocale);
        html.append("<div class='receipt-title'>").append(refundTitle).append(HTML_CLOSE_DIV);
        html.append(HTML_CLOSE_DIV);
        html.append(HTML_CLOSE_DIV);
    }

    /**
     * Appends the restaurant logo image to the receipt HTML.
     *
     * @param html       the StringBuilder to append logo to
     * @param restaurant the restaurant entity containing logo URL
     */
    private void appendRestaurantLogo(StringBuilder html, Restaurant restaurant) {
        try {
            String logoKeyOrUrl = restaurant.getLogoUrl();
            String signedLogoUrl = awsService.getPreSignedUrl(logoKeyOrUrl);
            if (signedLogoUrl != null && !signedLogoUrl.isEmpty() && !signedLogoUrl.equals("location")) {
                String safeUrl = signedLogoUrl.replace("'", "&#39;").replace("\"", "&quot;");
                html.append("<img class='logo' src=\"").append(safeUrl).append("\" alt=\"logo\"/>");
            }
        } catch (Exception e) {
            log.error("Error generating presigned URL for restaurant logo: {}", restaurant.getLogoUrl(), e);
        }
    }

    private void registerBundledJapaneseFontBestEffort(com.itextpdf.html2pdf.resolver.font.DefaultFontProvider fontProvider) {
        if (fontProvider == null) {
            return;
        }
        try (InputStream fontStream = Objects.requireNonNull(
                getClass().getResourceAsStream("/fonts/NotoSansCJKjp-Regular.otf"),
                "Bundled Japanese font '/fonts/NotoSansCJKjp-Regular.otf' is missing from resources"
        )) {
            byte[] fontBytes = fontStream.readAllBytes();
            FontProgram fontProgram = FontProgramFactory.createFont(fontBytes);
            fontProvider.addFont(fontProgram);
        } catch (Exception ex) {
            log.warn("Bundled Japanese font not found or failed to register for refund receipts; Japanese headers may not render correctly: {}", ex.getMessage());
        }
    }

    /**
     * Appends refund information section including refund number, date, and order details.
     *
     * @param html        the StringBuilder to append refund info to
     * @param refund      the refund entity
     * @param transaction the transaction entity
     * @param order       the order entity
     * @param userLocale  locale for localized text
     */
    private void appendRefundInfo(StringBuilder html, Refund refund, Transaction transaction, Order order, Locale userLocale) {
        html.append("<div class='refund-info'>");
        
        String lang = userLocale.getLanguage();
        ZoneId chainZone = resolveChainZoneId();
        String refundNumberLabel = messageUtil.getMessage("receipt.refund.number", userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(refundNumberLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(refund.getRefundNumber())).append(HTML_CLOSE_DIV);
        
        String refundDate = DateTimeUtil.format(
                refund.getCompletedAt() != null
                        ? refund.getCompletedAt().toInstant().atZone(chainZone).toLocalDateTime()
                        : LocalDateTime.now(chainZone),
                lang);
        String refundDateLabel = messageUtil.getMessage("receipt.refund.order.date", userLocale, messageUtil.getMessage("receipt.date", userLocale));
        html.append(HTML_OPEN_DIV_STRONG).append(refundDateLabel).append(HTML_CLOSE_STRONG_SPACE).append(refundDate).append(HTML_CLOSE_DIV);
        
        if (order.getCreatedAt() != null) {
            String orderDate = DateTimeUtil.format(order.getCreatedAt().toInstant().atZone(chainZone).toLocalDateTime(), lang);
            String orderDateLabel = messageUtil.getMessage("receipt.refund.order.date", userLocale, "Order Date");
            html.append(HTML_OPEN_DIV_STRONG).append(orderDateLabel).append(HTML_CLOSE_STRONG_SPACE).append(orderDate).append(HTML_CLOSE_DIV);
        }
        
        String refundTxnLabel = messageUtil.getMessage("receipt.transaction.number", userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(refundTxnLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(transaction.getTransactionNumber())).append(HTML_CLOSE_DIV);

        var chain = restaurantChainConfigProperties.getChain();
        String paymentMethodDisplay = receiptUtil.getPaymentMethodDisplayName(transaction.getPaymentMethod(), chain, userLocale);
        String paymentMethodLabel = messageUtil.getMessage("receipt.payment.method", userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(paymentMethodLabel).append(HTML_CLOSE_STRONG_SPACE)
                .append(receiptUtil.escapeHtml(paymentMethodDisplay)).append(HTML_CLOSE_DIV);
        
        if (refund.getRefundMethod() != null && !refund.getRefundMethod().isBlank()) {
            String refundMethodLabel = messageUtil.getMessage("receipt.refund.method", userLocale, "Refund Method");
            html.append(HTML_OPEN_DIV_STRONG).append(refundMethodLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(refund.getRefundMethod())).append(HTML_CLOSE_DIV);
        }
        
        if (refund.getCompletedBy() != null) {
            String staffName = NameUtil.formatName(
                    refund.getCompletedBy().getFirstName(),
                    refund.getCompletedBy().getLastName(),
                    lang);
            String cashierLabel = messageUtil.getMessage("receipt.cashier", userLocale);
            html.append(HTML_OPEN_DIV_STRONG).append(cashierLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(staffName)).append(HTML_CLOSE_DIV);
        }
        
        html.append(HTML_CLOSE_DIV);
    }

    private ZoneId resolveChainZoneId() {
        String tz = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getTimezone()
                : null;
        if (tz == null || tz.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(tz.trim());
        } catch (Exception ex) {
            log.warn("Invalid chain timezone '{}'; falling back to UTC", tz);
            return ZoneOffset.UTC;
        }
    }

    /**
     * Appends the refunded items table section with item details and refund amounts.
     *
     * @param html              the StringBuilder to append items to
     * @param refundItems       list of refund items to display
     * @param currencySymbol    currency symbol for formatting amounts
     * @param userLocale        locale for localized text
     * @param taxConfig         tax display configuration for alcoholic/non-alcoholic tax breakdown
     * @param hasAlcoholicItems whether the refund contains any alcoholic items
     */
    private void appendRefundedItems(StringBuilder html, List<RefundItem> refundItems, String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig, boolean hasAlcoholicItems) {
        html.append("<table class='items-table'>");
        html.append("<thead><tr>");
        html.append("<th class='item-name'>")
                .append(messageUtil.getMessage("receipt.item", userLocale))
                .append(HTML_CLOSE_TH);
        html.append("<th class='item-qty'>")
                .append(messageUtil.getMessage("receipt.quantity", userLocale))
                .append(HTML_CLOSE_TH);
        html.append("<th class='item-price'>")
                .append(messageUtil.getMessage("receipt.price", userLocale))
                .append(HTML_CLOSE_TH);
        html.append("</tr></thead>");
        html.append("<tbody>");
        
        for (RefundItem refundItem : refundItems) {
            OrderedItem orderedItem = refundItem.getOrderedItem();
            OrderedCombo orderedCombo = refundItem.getOrderedCombo();
            int lineQty = refundItem.getQuantity() != null ? refundItem.getQuantity() : 1;
            
            if (orderedItem != null) {
                appendOrderedItemRow(html, orderedItem, refundItem, lineQty, currencySymbol, userLocale, taxConfig);
            } else if (orderedCombo != null) {
                appendOrderedComboRows(html, orderedCombo, refundItem, lineQty, currencySymbol, userLocale, taxConfig);
            }
        }
        
        html.append("</tbody></table>");
        
        // Legend explains what the * marker means; show it only when the marker is actually used.
        if (taxConfig.useAlcoholMarker && hasAlcoholicItems) {
            html.append("<div style='font-size: 9px; margin-top: 6px; text-align: left;'>");
            html.append("* ").append(messageUtil.getMessage("receipt.alcoholic.item.indicator", userLocale, "Indicates alcoholic item"));
            html.append(HTML_CLOSE_DIV);
        }
    }

    /**
     * Appends a table row for a refunded ordered item.
     *
     * @param html          the StringBuilder to append row to
     * @param orderedItem   the ordered item entity
     * @param refundItem   the refund item containing refund details
     * @param lineQty      the quantity being refunded for this line
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale   locale for localized text
     */
    private void appendOrderedItemRow(StringBuilder html, OrderedItem orderedItem, RefundItem refundItem,
                                       int lineQty, String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
        String itemName = receiptUtil.getLocalizedName(orderedItem.getItem().getTranslations(), userLocale, "Item");
        
        // Add * marker for alcoholic items if tax rates differ
        if (taxConfig.useAlcoholMarker && isAlcoholicItem(orderedItem)) {
            itemName = itemName + "*";
        }
        
        html.append("<tr>");
        html.append("<td class='item-name'>").append(receiptUtil.escapeHtml(itemName)).append(HTML_CLOSE_TD);
        html.append("<td class='item-qty'>").append(lineQty).append(HTML_CLOSE_TD);
        html.append("<td class='item-price'>").append(currencySymbol)
                .append(CurrencyFormatter.formatAmount(refundItem.getRefundAmount(), currencySymbol))
                .append(HTML_CLOSE_TD);
        html.append(HTML_CLOSE_TR);
        
        // Modifiers (description only, hide price like main receipt)
        appendItemModifierRows(html, orderedItem, userLocale);
    }

    /**
     * Appends table rows for a refunded ordered combo including child items.
     *
     * @param html          the StringBuilder to append rows to
     * @param orderedCombo  the ordered combo entity
     * @param refundItem    the refund item containing refund details
     * @param lineQty       the quantity being refunded for this line
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale    locale for localized text
     */
    private void appendOrderedComboRows(StringBuilder html, OrderedCombo orderedCombo, RefundItem refundItem,
                                         int lineQty, String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
        String comboName = receiptUtil.getLocalizedName(orderedCombo.getCombo().getTranslations(), userLocale, "Combo");
        html.append("<tr>");
        html.append("<td class='item-name'>").append(receiptUtil.escapeHtml(comboName)).append(HTML_CLOSE_TD);
        html.append("<td class='item-qty'>").append(lineQty).append(HTML_CLOSE_TD);
        html.append("<td class='item-price'>").append(currencySymbol)
                .append(CurrencyFormatter.formatAmount(refundItem.getRefundAmount(), currencySymbol))
                .append(HTML_CLOSE_TD);
        html.append(HTML_CLOSE_TR);
        
        // Child items under combo (description only, no price)
        if (orderedCombo.getOrderedItems() != null && !orderedCombo.getOrderedItems().isEmpty()) {
            for (OrderedItem ci : orderedCombo.getOrderedItems()) {
                String ciName = receiptUtil.getLocalizedName(ci.getItem().getTranslations(), userLocale, "");
                
                // Add * marker for alcoholic items if tax rates differ
                if (taxConfig.useAlcoholMarker && isAlcoholicItem(ci)) {
                    ciName = ciName + "*";
                }
                
                html.append("<tr>");
                html.append("<td class='item-sub'>- ").append(receiptUtil.escapeHtml(ciName)).append(HTML_CLOSE_TD);
                html.append(HTML_EMPTY_QTY_CELL);
                html.append(HTML_EMPTY_PRICE_CELL);
                html.append(HTML_CLOSE_TR);
                
                // Modifiers for child item
                appendItemModifierRows(html, ci, userLocale);
            }
        }
    }

    /**
     * Appends table rows for item modifiers associated with an ordered item.
     *
     * @param html        the StringBuilder to append rows to
     * @param orderedItem the ordered item entity
     * @param userLocale  locale for localized text
     */
    private void appendItemModifierRows(StringBuilder html, OrderedItem orderedItem, Locale userLocale) {
        if (orderedItem.getOrderedItemModifiers() == null || orderedItem.getOrderedItemModifiers().isEmpty()) {
            return;
        }
        for (OrderedItemModifier mod : orderedItem.getOrderedItemModifiers()) {
            String modName = receiptUtil.getLocalizedName(mod.getModifierItem().getTranslations(), userLocale, "");
            if (modName == null || modName.isBlank()) {
                continue;
            }
            html.append("<tr>");
            html.append("<td class='item-mod'>+ ").append(receiptUtil.escapeHtml(modName)).append(HTML_CLOSE_TD);
            html.append(HTML_EMPTY_QTY_CELL);
            html.append(HTML_EMPTY_PRICE_CELL);
            html.append(HTML_CLOSE_TR);
        }
    }

    /**
     * Appends the refund totals section including subtotal, tax, service charge, and total refund amount.
     *
     * @param html              the StringBuilder to append totals to
     * @param refund            the refund entity
     * @param currencySymbol    currency symbol for formatting amounts
     * @param userLocale        locale for localized text
     * @param chain             restaurant chain configuration data
     * @param taxConfig         tax display configuration for alcoholic/non-alcoholic tax breakdown
     * @param order             the order entity
     * @param hasAlcoholicItems whether the refund contains any alcoholic items
     */
    private void appendRefundTotals(StringBuilder html, Refund refund, String currencySymbol, Locale userLocale,
                                   RestaurantChainConfigProperties.RestaurantChainData chain, TaxDisplayConfig taxConfig) {
        html.append("<div class='totals'>");

        // Print order aligned with main receipt totals ordering (refund-specific amounts)
        appendRefundSubtotalLine(html, refund, currencySymbol, userLocale);
        appendRefundDiscountLine(html, refund, currencySymbol, userLocale);
        appendRefundServiceChargeLine(html, refund, currencySymbol, userLocale, chain);
        appendRefundPackingChargeLine(html, refund, currencySymbol, userLocale, chain);
        appendRefundTaxBreakdownLines(html, refund, currencySymbol, userLocale, taxConfig);
        appendRefundAdditionalDiscountLine(html, refund, currencySymbol, userLocale);
        appendRefundTotalLine(html, refund, currencySymbol, userLocale);

        html.append(HTML_CLOSE_DIV);
    }

    private void appendRefundSubtotalLine(StringBuilder html, Refund refund, String currencySymbol, Locale userLocale) {
        if (refund.getSubtotalRefundAmount() == null || refund.getSubtotalRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String label = messageUtil.getMessage("receipt.refund.subtotal", userLocale);
        receiptUtil.appendTotalLine(html, label, refund.getSubtotalRefundAmount(), currencySymbol, false, false);
    }

    private void appendRefundServiceChargeLine(StringBuilder html,
                                               Refund refund,
                                               String currencySymbol,
                                               Locale userLocale,
                                               RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (refund.getServiceChargeRefundAmount() == null || refund.getServiceChargeRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.refund.service.charge", userLocale);
        String label = baseLabel;
        if (chain != null && chain.getServiceChargesForDineIn() != null) {
            RestaurantChainConfigProperties.ServiceChargesForDineIn serviceCharge = chain.getServiceChargesForDineIn();
            label = buildChargeLabel(baseLabel, serviceCharge.getValue(), serviceCharge.getType(), currencySymbol, userLocale);
        }
        receiptUtil.appendTotalLine(html, label, refund.getServiceChargeRefundAmount(), currencySymbol, false, false);
    }

    private void appendRefundPackingChargeLine(StringBuilder html,
                                               Refund refund,
                                               String currencySymbol,
                                               Locale userLocale,
                                               RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (refund.getPackingChargeRefundAmount() == null || refund.getPackingChargeRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.packing.charge", userLocale);
        String label = baseLabel;
        if (chain != null && chain.getPackingChargesForTakeaway() != null) {
            RestaurantChainConfigProperties.PackingChargesForTakeaway packingCharge = chain.getPackingChargesForTakeaway();
            label = buildChargeLabel(baseLabel, packingCharge.getValue(), packingCharge.getType(), currencySymbol, userLocale);
        }
        receiptUtil.appendTotalLine(html, label, refund.getPackingChargeRefundAmount(), currencySymbol, false, false);
    }

    private void appendRefundTaxBreakdownLines(StringBuilder html,
                                               Refund refund,
                                               String currencySymbol,
                                               Locale userLocale,
                                               TaxDisplayConfig taxConfig) {
        if (refund.getTaxRefundAmount() == null || refund.getTaxRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal alcoholicTax = refund.getAlcoholicTaxRefundAmount() != null ? refund.getAlcoholicTaxRefundAmount() : BigDecimal.ZERO;
        BigDecimal nonAlcoholicTax = refund.getNonAlcoholicTaxRefundAmount() != null ? refund.getNonAlcoholicTaxRefundAmount() : BigDecimal.ZERO;

        BigDecimal alcoholicTaxable = resolveTaxableRefundAmount(
                refund.getAlcoholicTaxableRefundAmount(),
                alcoholicTax,
                taxConfig != null ? taxConfig.alcoholicTaxCharge : null);
        BigDecimal nonAlcoholicTaxable = resolveTaxableRefundAmount(
                refund.getNonAlcoholicTaxableRefundAmount(),
                nonAlcoholicTax,
                taxConfig != null ? taxConfig.nonAlcoholicTaxCharge : null);

        appendTaxableAndTaxLines(html, currencySymbol, userLocale,
                alcoholicTaxable, alcoholicTax,
                taxConfig != null ? taxConfig.alcoholicTaxCharge : null,
                new TaxLineLabels(MSG_RECEIPT_ALCOHOLIC_TAXABLE_AMOUNT, MSG_RECEIPT_ALCOHOLIC_ITEM_TAX,
                        "Alcoholic Taxable Amount", "Alcoholic Tax"));

        appendTaxableAndTaxLines(html, currencySymbol, userLocale,
                nonAlcoholicTaxable, nonAlcoholicTax,
                taxConfig != null ? taxConfig.nonAlcoholicTaxCharge : null,
                new TaxLineLabels(MSG_RECEIPT_NON_ALCOHOLIC_TAXABLE_AMOUNT, MSG_RECEIPT_NON_ALCOHOLIC_ITEM_TAX,
                        "Non-Alcoholic Taxable Amount", "Non-Alcoholic Tax"));
    }

    private record TaxLineLabels(String taxableMessageKey, String taxMessageKey, String taxableFallbackLabel, String taxFallbackLabel) {
    }

    private void appendTaxableAndTaxLines(StringBuilder html,
                                          String currencySymbol,
                                          Locale userLocale,
                                          BigDecimal taxableAmount,
                                          BigDecimal taxAmount,
                                          RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge,
                                          TaxLineLabels labels) {
        if (taxableAmount != null && taxableAmount.compareTo(BigDecimal.ZERO) > 0) {
            String taxableLabel = resolveTaxableLabel(userLocale, labels.taxableMessageKey(), labels.taxableFallbackLabel(), taxCharge);
            receiptUtil.appendTotalLine(html, taxableLabel, taxableAmount, currencySymbol, false, false);
        }
        if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
            String taxLabel = resolveTaxLabel(currencySymbol, userLocale, labels.taxMessageKey(), labels.taxFallbackLabel(), taxCharge);
            receiptUtil.appendTotalLine(html, taxLabel, taxAmount, currencySymbol, false, false);
        }
    }

    private String resolveTaxableLabel(Locale userLocale,
                                      String messageKey,
                                      String fallbackLabel,
                                      RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge) {
        if (isJapanese(userLocale)) {
            String rateArg = formatPercentArg(taxCharge);
            return rateArg != null
                    ? messageUtil.getMessage(messageKey, userLocale, rateArg)
                    : messageUtil.getMessage(messageKey, userLocale);
        }
        return messageUtil.getMessage(messageKey, userLocale, fallbackLabel);
    }

    private String resolveTaxLabel(String currencySymbol,
                                  Locale userLocale,
                                  String messageKey,
                                  String fallbackLabel,
                                  RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge) {
        if (isJapanese(userLocale)) {
            String rateArg = formatPercentArg(taxCharge);
            return rateArg != null
                    ? messageUtil.getMessage(messageKey, userLocale, rateArg)
                    : messageUtil.getMessage(messageKey, userLocale);
        }

        String baseLabel = messageUtil.getMessage(messageKey, userLocale, fallbackLabel);
        if (taxCharge == null) {
            return baseLabel;
        }
        return buildChargeLabel(baseLabel, taxCharge.getValue(), taxCharge.getType(), currencySymbol, userLocale);
    }

    private void appendRefundDiscountLine(StringBuilder html, Refund refund, String currencySymbol, Locale userLocale) {
        if (refund.getDiscountRefundAmount() == null || refund.getDiscountRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String label = messageUtil.getMessage("receipt.discount", userLocale);
        receiptUtil.appendTotalLine(html, label, refund.getDiscountRefundAmount(), currencySymbol, false, false);
    }

    private void appendRefundAdditionalDiscountLine(StringBuilder html, Refund refund, String currencySymbol, Locale userLocale) {
        if (refund.getAdditionalDiscountRefundAmount() == null || refund.getAdditionalDiscountRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String label = messageUtil.getMessage("receipt.additional.discount", userLocale);
        receiptUtil.appendTotalLine(html, label, refund.getAdditionalDiscountRefundAmount(), currencySymbol, false, false);
    }

    private void appendRefundTotalLine(StringBuilder html, Refund refund, String currencySymbol, Locale userLocale) {
        String totalLabel = messageUtil.getMessage("receipt.refund.total", userLocale);
        receiptUtil.appendTotalLine(html, totalLabel, refund.getTotalRefundAmount(), currencySymbol, false, true);
    }

    /**
     * Appends the receipt footer section with thank you message.
     *
     * @param html       the StringBuilder to append footer to
     * @param userLocale locale for localized thank you message
     */
    private void appendReceiptFooter(StringBuilder html, Locale userLocale) {
        html.append("<div class='footer'>");
        html.append("<div class='thank-you'>").append(messageUtil.getMessage("receipt.thank.you", userLocale)).append(HTML_CLOSE_DIV);
        html.append(HTML_CLOSE_DIV);
    }

    private boolean isJapanese(Locale locale) {
        return locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
    }

    private String formatPercentArg(RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge) {
        if (taxCharge == null || taxCharge.getType() != ChargeType.PERCENT) {
            return null;
        }
        double value = taxCharge.getValue();
        int intValue = (int) Math.round(value);
        if (Math.abs(value - intValue) < 0.01) {
            return String.valueOf(intValue);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String chainCountryName() {
        var chain = restaurantChainConfigProperties.getChain();
        return chain != null ? chain.getCountryName() : null;
    }
}
