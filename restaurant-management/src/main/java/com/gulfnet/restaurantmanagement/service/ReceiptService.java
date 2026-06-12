package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.OrderedItemModifier;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.util.AddressDto;
import com.gulfnet.shared_library.util.AddressFormatter;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.restaurantmanagement.config.OnlineCardPaymentProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.restaurantmanagement.exception.ReceiptGenerationException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Service for generating receipt PDFs optimized for thermal printers.
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
public class ReceiptService {

    // HTML template constants
    private static final String HTML_DIVIDER = "<div class='divider'></div>";
    private static final String EMAIL_INDENT_44 = "                                            ";
    private static final String EMAIL_CLOSE_TH_LINE = "                                        </th>\n";
    private static final String EMAIL_CLOSE_TR_LINE = "                                    </tr>\n";
    private static final String EMAIL_CLOSE_P_LINE = "                                            </p>\n";
    private static final String EMAIL_CLOSE_TD_LINE = "                                        </td>\n";
    private static final String EMAIL_OPEN_RIGHT_TD_PADDED_16_LINE =
            "                                        <td align=\"right\" style=\"padding: 16px 0; font-size: 14px; line-height: 20px; color: #111827; font-weight: 500;\">\n";
    
    // Message key constants
    private static final String MSG_KEY_RECEIPT_TITLE = "receipt.title";
    private static final String MSG_RECEIPT_ALCOHOLIC_TAXABLE_AMOUNT = "receipt.alcoholic.taxable.amount";
    private static final String MSG_RECEIPT_ALCOHOLIC_ITEM_TAX = "receipt.alcoholic.item.tax";
    private static final String MSG_RECEIPT_NON_ALCOHOLIC_TAXABLE_AMOUNT = "receipt.non.alcoholic.taxable.amount";
    private static final String MSG_RECEIPT_NON_ALCOHOLIC_ITEM_TAX = "receipt.non.alcoholic.item.tax";
    private static final String HTML_EMPTY_QTY_CELL = "<td class='item-qty'></td>";
    private static final String HTML_OPEN_TD_PRICE = "<td class='item-price'>";
    private static final String HTML_CLOSE_DIV = "</div>";
    private static final String HTML_CLOSE_TD = "</td>";
    private static final String HTML_CLOSE_TR = "</tr>";
    private static final String HTML_CLOSE_TH = "</th>";
    private static final String HTML_OPEN_DIV_STRONG = "<div><strong>";
    private static final String HTML_CLOSE_STRONG_SPACE = ":</strong> ";

    /** Outer table row open/close (20-space indent) used in receipt email HTML. */
    private static final String EMAIL_TR_OPEN_20 = "                    <tr>\n";
    private static final String EMAIL_TR_CLOSE_20 = "                    </tr>\n";
    private static final String EMAIL_TD_CLOSE_24 = "                        </td>\n";
    private static final String EMAIL_TD_CLOSE_36 = "                                    </td>\n";
    private static final String EMAIL_TD_PAD_24_32_TOP = "                        <td style=\"padding: 24px 32px 0 32px;\">\n";
    private static final String EMAIL_TABLE_PRES_100 = "                            <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n";
    private static final String EMAIL_TABLE_CLOSE_28 = "                            </table>\n";
    private static final String EMAIL_P_CLOSE_40 = "                                        </p>\n";
    private static final String EMAIL_P_CLOSE_28 = "                            </p>\n";

    private final AWSService awsService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final MessageUtil messageUtil;
    private final OrderValidationService orderValidationService;
    private final EmailSender emailSender;
    private final ReceiptUtil receiptUtil;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final DiscountRepository discountRepository;
    private final OnlineCardPaymentProperties onlineCardPaymentProperties;

    /**
     * Generates a receipt PDF optimized for thermal printers (72mm or 80mm width) and uploads to S3
     * 
     * @param order The order entity
     * @param transaction The transaction
     * @param restaurant The restaurant
     * @param orderedItems List of ordered items
     * @return S3 URL of the generated PDF receipt
     */
    public String generateReceiptPdf(Order order, Transaction transaction, Restaurant restaurant, List<OrderedItem> orderedItems) {
        return generateReceiptPdf(order, transaction, restaurant, orderedItems, null);
    }

    /**
     * @param languageOverride optional; used only when chain {@code defaultLanguageCode} is not set (then request locale / en)
     */
    public String generateReceiptPdf(Order order, Transaction transaction, Restaurant restaurant, List<OrderedItem> orderedItems,
                                   String languageOverride) {
        try {
            log.info("Receipt generation START - orderId={}, transactionId={}, transactionNumber={}",
                    order != null ? order.getId() : null,
                    transaction != null ? transaction.getId() : null,
                    transaction != null ? transaction.getTransactionNumber() : null);

            Order nonNullOrder = Objects.requireNonNull(order, "order");
            Transaction nonNullTransaction = Objects.requireNonNull(transaction, "transaction");
            Restaurant nonNullRestaurant = Objects.requireNonNull(restaurant, "restaurant");

            String chainDefault = restaurantChainConfigProperties.getChain() != null
                    ? restaurantChainConfigProperties.getChain().getDefaultLanguageCode()
                    : null;
            String resolvedLang = DateTimeUtil.resolveReceiptDisplayLanguage(chainDefault, languageOverride);
            Locale targetLocale = Locale.forLanguageTag(resolvedLang);
            log.info("Receipt generation - resolved targetLocale='{}' for orderId={}, transactionId={}",
                    targetLocale.getLanguage(),
                    order != null ? order.getId() : null,
                    transaction != null ? transaction.getId() : null);

            log.info("Receipt generation - building HTML for orderId={}, transactionId={}",
                    order != null ? order.getId() : null,
                    transaction != null ? transaction.getId() : null);
            String htmlContent = generateReceiptHtml(nonNullOrder, nonNullTransaction, nonNullRestaurant, orderedItems, targetLocale);
            log.info("Receipt generation - HTML built successfully for orderId={}, transactionId={}, htmlLength={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId(),
                    htmlContent != null ? htmlContent.length() : 0);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Get receipt page size from chain configuration
            int receiptWidthMm = 72; // Default to 72mm
            int receiptMaxHeightMm = 0; // 0 means unlimited/continuous paper
            
            RestaurantChainConfigProperties.ReceiptPageSize pageSize = restaurantChainConfigProperties.getChain() != null
                    ? restaurantChainConfigProperties.getChain().getReceiptPageSize()
                    : null;
            
            if (pageSize != null && pageSize.getWidthMm() > 0) {
                receiptWidthMm = pageSize.getWidthMm();
                receiptMaxHeightMm = pageSize.getMaxHeightMm() > 0 ? pageSize.getMaxHeightMm() : 0;
            }
            log.info("Receipt generation - using page configuration orderId={}, transactionId={}, widthMm={}, maxHeightMm={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId(),
                    receiptWidthMm,
                    receiptMaxHeightMm);

            // Convert mm to points (1mm = 2.83465 points)
            float widthPoints = receiptWidthMm * 2.83465f;
            float heightPoints = receiptMaxHeightMm > 0 ? receiptMaxHeightMm * 2.83465f : 600f;
            
            // Create custom page size for thermal receipt
            Rectangle pageSizeRect = new Rectangle(widthPoints, heightPoints);

            // Configure converter properties for Unicode support
            com.itextpdf.html2pdf.ConverterProperties properties = new com.itextpdf.html2pdf.ConverterProperties();
            properties.setCharset("UTF-8");
            
            // Configure font provider.
            // First register system/default fonts, then explicitly add bundled CJK font for Japanese.
            com.itextpdf.html2pdf.resolver.font.DefaultFontProvider fontProvider =
                    new com.itextpdf.html2pdf.resolver.font.DefaultFontProvider(false, false, false);
            fontProvider.addStandardPdfFonts();
            fontProvider.addSystemFonts();
            registerBundledJapaneseFontBestEffort(fontProvider);
            properties.setFontProvider(fontProvider);

            // Create PdfDocument with custom page size and convert HTML to PDF
            log.info("Receipt generation - starting HTML to PDF conversion for orderId={}, transactionId={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId());
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
            com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            PageSize customPageSize = new PageSize(pageSizeRect);
            pdfDoc.setDefaultPageSize(customPageSize);
            
            HtmlConverter.convertToPdf(htmlContent, pdfDoc, properties);
            pdfDoc.close();
            byte[] pdfBytes = baos.toByteArray();
            log.info("Receipt generation - HTML to PDF conversion completed for orderId={}, transactionId={}, pdfSizeBytes={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId(),
                    pdfBytes.length);

            InputStream inputStream = new ByteArrayInputStream(pdfBytes);
            String fileName = "receipt_" + nonNullTransaction.getTransactionNumber() + ".pdf";
            String s3Key = "payment/order/" + nonNullOrder.getId().toString() + "/" + targetLocale.getLanguage().toLowerCase() + "/" + fileName;
            
            // Upload PDF with Content-Type header for proper browser preview
            log.info("Receipt generation - uploading PDF to S3 for orderId={}, transactionId={}, s3Key={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId(),
                    s3Key);
            String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, pdfBytes.length, "application/pdf");
            log.info("Receipt PDF (locale={}, width={}mm) uploaded to S3 - URL: {}", 
                    targetLocale.getLanguage(), receiptWidthMm, uploadedFileUrl);

            log.info("Receipt generation SUCCESS - orderId={}, transactionId={}, receiptUrl={}",
                    nonNullOrder.getId(),
                    nonNullTransaction.getId(),
                    uploadedFileUrl);
            return uploadedFileUrl;
            
        } catch (Exception e) {
            log.error("Receipt generation FAILED - orderId={}, transactionId={}, error={}",
                    order != null ? order.getId() : null,
                    transaction != null ? transaction.getId() : null,
                    e.getMessage(), e);
            throw new ReceiptGenerationException("Failed to generate receipt PDF", e);
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
            log.warn("Bundled Japanese font not found or failed to register; Japanese headers may not render correctly: {}", ex.getMessage());
        }
    }

    /**
     * Generates HTML content for receipt optimized for thermal printers.
     * Optimized for narrow width (72mm/80mm) with responsive design.
     */
    private String generateReceiptHtml(Order order, Transaction transaction, Restaurant restaurant, 
                                       List<OrderedItem> orderedItems, Locale locale) {
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
            appendOrderInfo(html, transaction, order, userLocale);
            html.append(HTML_DIVIDER);
            
            // Determine tax comparison and marker usage
            TaxDisplayConfig taxConfig = determineTaxDisplayConfig(order, chain);
            
            appendItemsTable(html, orderedItems, order, currencySymbol, userLocale, taxConfig);
            html.append(HTML_DIVIDER);
            appendTotals(html, order, currencySymbol, userLocale, chain, taxConfig);
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
     * Resolves consumption-tax taxable base amounts for display.
     *
     * New orders/refunds should already persist `*TaxableAmount` fields.
     * For older records (null taxable fields), we infer the base from the tax amount
     * when the configured tax type is percentage-based.
     */
    private BigDecimal resolveTaxableAmount(BigDecimal storedTaxableAmount,
                                              BigDecimal taxAmount,
                                              RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge) {
        if (storedTaxableAmount != null) {
            return storedTaxableAmount;
        }
        if (taxAmount == null || taxCharge == null) {
            return null;
        }
        if (taxCharge.getType() == ChargeType.PERCENT && taxCharge.getValue() != 0) {
            // taxAmount = taxableBase * (rate / 100) => taxableBase = taxAmount * 100 / rate
            return taxAmount.multiply(BigDecimal.valueOf(100))
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
     * Checks if there are any alcoholic items in the order (including combo items)
     *
     * @param orderedItems list of ordered items
     * @param order the order entity
     * @return true if any alcoholic items are found, false otherwise
     */
    private boolean checkForAlcoholicItems(List<OrderedItem> orderedItems, Order order) {
        // Check regular ordered items
        for (OrderedItem item : orderedItems) {
            if (isComboChildItem(item) || item.getItemStatus() == ItemStatus.CANCELED) {
                continue;
            }
            if (isAlcoholicItem(item)) {
                return true;
            }
        }
        
        // Check combo child items
        if (order.getOrderedCombos() != null) {
            for (var oc : order.getOrderedCombos()) {
                if (oc.getItemStatus() == ItemStatus.CANCELED) {
                    continue;
                }
                if (oc.getOrderedItems() != null) {
                    for (OrderedItem ci : oc.getOrderedItems()) {
                        if (isAlcoholicItem(ci)) {
                            return true;
                        }
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
        
        // Header styles - compact two-column layout (logo left, details right)
        html.append(".header { display: flex; align-items: stretch; gap: 1px; margin-bottom: 0; padding-bottom: 0; }");
        html.append(".header-logo { width: 58px; height: 58px; flex: 0 0 58px; display: flex; align-items: center; justify-content: center; overflow: hidden; }");
        html.append(".logo { width: 100%; height: 100%; margin: 0; display: block; object-fit: cover; }");
        html.append(".header-details { flex: 1; min-width: 0; text-align: left; margin-left: 18px;}");
        html.append(".restaurant-name { font-size: 13px; font-weight: bold; margin: 0 0 1px 0; text-transform: uppercase; letter-spacing: 0.25px; word-break: break-all;}");
        html.append(".address { font-size: 9px; margin: 0 0 1px 0; white-space: pre-line; word-break: break-word; }");
        html.append(".receipt-title { font-size: 12px; font-weight: bold; margin: 0; text-transform: uppercase; letter-spacing: 1px; }");
        
        // Order info styles - clean spacing
        html.append(".order-info { margin-bottom: 6px; font-size: 10px; }");
        html.append(".order-info div { margin: 2px 0; }");
        html.append(".order-info strong { font-weight: bold; }");
        
        // Items table styles - elegant table design
        html.append(".items-table { width: 100%; border-collapse: collapse; margin-bottom: 2px; font-size: 10px; }");
        html.append(".items-table th, .items-table td { padding: 2px 1px; text-align: left; }");
        html.append(".items-table th { font-weight: bold; font-size: 9px; text-transform: uppercase; letter-spacing: 0.5px; padding-bottom: 2px; }");
        html.append(".items-table .item-name { word-break: break-word; max-width: 55%; }");
        html.append(".items-table .item-qty { text-align: center; width: 15%; }");
        html.append(".items-table .item-price { text-align: right; width: 30%; }");
        // Use same text color for discount rows as normal items
        html.append(".items-table .item-discount { font-size: 9px; color: #000; padding-left: 12px; }");
        html.append(".items-table .item-mod { font-size: 9px; color: #000; padding-left: 12px; }");
        html.append(".items-table .item-sub { font-size: 9px; color: #000; padding-left: 8px; }");
        
        // Totals styles - single elegant line, all amounts right-aligned
        html.append(".totals { margin-top: 0; padding-top: 0; }");
        html.append(".total-line { display: flex; justify-content: space-between; align-items: center; margin: 2px 0; font-size: 10px; }");
        html.append(".total-line.total-final { font-weight: bold; font-size: 12px; border-top: 1px solid #000; padding-top: 3px; margin-top: 4px; }");
        html.append(".total-label { text-align: left; flex: 1; }");
        html.append(".total-value { text-align: right; white-space: nowrap; }");
        
        // Payment info styles - minimal design
        html.append(".payment-info { margin-top: 6px; font-size: 10px; }");
        html.append(".payment-info div { display: flex; justify-content: space-between; align-items: center; margin: 2px 0; }");
        html.append(".payment-info strong { font-weight: bold; }");
        html.append(".payment-label { text-align: left; flex: 1; }");
        html.append(".payment-value { text-align: right; white-space: nowrap; }");
        
        // Footer styles - elegant spacing
        html.append(".footer { text-align: center; margin-top: 7px; padding-top: 3px; }");
        html.append(".thank-you { font-weight: bold; font-size: 11px; margin-bottom: 2px; }");
        
        // Divider style - clean section separator
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
            html.append("<div class='address'>").append(receiptUtil.escapeHtml(formattedAddress)).append(HTML_CLOSE_DIV);
        }
        if (restaurant.getPhoneNumber() != null && !restaurant.getPhoneNumber().isBlank()) {
            String phoneLabel = messageUtil.getMessage("receipt.phone", userLocale);
            html.append("<div class='address'><strong>").append(phoneLabel).append(HTML_CLOSE_STRONG_SPACE)
                    .append(receiptUtil.escapeHtml(restaurant.getPhoneNumber().trim())).append(HTML_CLOSE_DIV);
        }
        if (restaurant.getGstNumber() != null && !restaurant.getGstNumber().isBlank()) {
            String gstLabel = messageUtil.getMessage("receipt.gst.number", userLocale);
            html.append("<div class='address'><strong>").append(gstLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(restaurant.getGstNumber())).append(HTML_CLOSE_DIV);
        }
        String receiptTitle = messageUtil.getMessage(MSG_KEY_RECEIPT_TITLE, userLocale);
        html.append("<div class='receipt-title'>").append(receiptTitle).append(HTML_CLOSE_DIV);
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

    /**
     * Appends order information section including transaction number, date, order number, payment method, and cashier.
     *
     * @param html        the StringBuilder to append order info to
     * @param transaction the transaction entity
     * @param order       the order entity
     * @param userLocale  locale for localized text
     */
    private void appendOrderInfo(StringBuilder html, Transaction transaction, Order order, Locale userLocale) {
        html.append("<div class='order-info'>");
        String transactionNumberLabel = messageUtil.getMessage("receipt.transaction.number", userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(transactionNumberLabel).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(transaction.getTransactionNumber())).append(HTML_CLOSE_DIV);
        
        // Display time in the chain-configured timezone.
        String lang = userLocale.getLanguage();
        String dateTime = DateTimeUtil.format(LocalDateTime.now(resolveChainZoneId()), lang);
        String dateLabel = messageUtil.getMessage("receipt.date", userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(dateLabel).append(HTML_CLOSE_STRONG_SPACE).append(dateTime).append(HTML_CLOSE_DIV);
        
        html.append(HTML_OPEN_DIV_STRONG).append(messageUtil.getMessage("receipt.order.number", userLocale)).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(order.getOrderNumber())).append(HTML_CLOSE_DIV);

        var chain = restaurantChainConfigProperties.getChain();
        String paymentMethodDisplay = receiptUtil.getPaymentMethodDisplayName(transaction.getPaymentMethod(), chain, userLocale);
        html.append(HTML_OPEN_DIV_STRONG).append(messageUtil.getMessage("receipt.payment.method", userLocale))
                .append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(paymentMethodDisplay)).append(HTML_CLOSE_DIV);
        String cashierName = resolveCashierDisplayName(transaction, lang);
        if (cashierName != null && !cashierName.isBlank()) {
            html.append(HTML_OPEN_DIV_STRONG).append(messageUtil.getMessage("receipt.cashier", userLocale)).append(HTML_CLOSE_STRONG_SPACE).append(receiptUtil.escapeHtml(cashierName)).append(HTML_CLOSE_DIV);
        }
        html.append(HTML_CLOSE_DIV);
    }

    /**
     * Cashier line for receipts: config label for customer hosted card; staff name when a real cashier paid.
     */
    private String resolveCashierDisplayName(Transaction transaction, String lang) {
        if (transaction == null) {
            return null;
        }
        if (transaction.getPaymentInitiatorType() != null
                && transaction.getPaymentInitiatorType() == Transaction.PAYMENT_INITIATOR_CUSTOMER) {
            String name = onlineCardPaymentProperties.getUserName();
            return name != null && !name.isBlank() ? name.trim() : "Online Card Payment";
        }
        User cashier = transaction.getCashier();
        if (cashier == null) {
            return null;
        }
        return NameUtil.formatName(cashier.getFirstName(), cashier.getLastName(), lang);
    }

    /**
     * Appends the items table section with ordered items and combos.
     *
     * @param html          the StringBuilder to append items table to
     * @param orderedItems list of ordered items to display
     * @param order         the order entity containing combos
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale    locale for localized text
     */
    private void appendItemsTable(StringBuilder html, List<OrderedItem> orderedItems, Order order,
                                   String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
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
        
        // Track if any alcoholic items are present (for legend display)
        boolean hasAlcoholicItems = false;
        
        // Normal ordered items (exclude combo items and canceled items)
        for (OrderedItem item : orderedItems) {
            if (isComboChildItem(item) || item.getItemStatus() == ItemStatus.CANCELED) {
                continue;
            }
            // Check if this item is alcoholic
            if (isAlcoholicItem(item)) {
                hasAlcoholicItems = true;
            }
            appendOrderedItemRows(html, item, currencySymbol, userLocale, taxConfig);
        }
        
        // Ordered combos (exclude canceled combos)
        boolean hasAlcoholicItemsInCombos = appendOrderedComboRows(html, order, currencySymbol, userLocale, taxConfig);
        if (hasAlcoholicItemsInCombos) {
            hasAlcoholicItems = true;
        }
        
        html.append("</tbody></table>");
        
        // Legend explains what the * marker means; show it only when the marker is actually used.
        if (taxConfig.useAlcoholMarker && hasAlcoholicItems) {
            html.append("<div style='font-size: 9px; margin-top: 6px; text-align: left;'>");
            html.append("* ").append(messageUtil.getMessage("receipt.alcoholic.item.indicator", userLocale, "Indicates alcoholic item"));
            html.append(HTML_CLOSE_DIV);
        }
    }

    private boolean isComboChildItem(OrderedItem item) {
        try {
            var mCombo = item.getClass().getMethod("getOrderedCombo");
            Object vCombo = mCombo.invoke(item);
            return vCombo != null;
        } catch (Exception e) {
            log.trace("Could not determine combo status for item via reflection", e);
            return false;
        }
    }

    /**
     * Appends table rows for an ordered item including modifiers and discount information.
     *
     * @param html          the StringBuilder to append rows to
     * @param item          the ordered item entity
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale    locale for localized text
     * @param taxConfig     tax display configuration for alcoholic/non-alcoholic tax breakdown
     */
    private void appendOrderedItemRows(StringBuilder html, OrderedItem item, String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
        String itemName = receiptUtil.getLocalizedName(item.getItem().getTranslations(), userLocale, "Unknown Item");
        
        // Add * marker for alcoholic items if tax rates differ
        if (taxConfig.useAlcoholMarker && isAlcoholicItem(item)) {
            itemName = itemName + "*";
        }
        
        // Amounts
        BigDecimal totalItemAmount = item.getTotalItemAmount() != null ? item.getTotalItemAmount() : BigDecimal.ZERO; // gross (base + modifiers)
        BigDecimal totalDiscountedAmount = item.getTotalDiscountedItemAmount() != null ? item.getTotalDiscountedItemAmount() : totalItemAmount; // net after discount

        int itemQty = item.getQuantity() != null ? item.getQuantity() : 1;
        BigDecimal baseUnitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal baseLineTotal = baseUnitPrice.multiply(BigDecimal.valueOf(itemQty));
        
        // Calculate discount
        BigDecimal discountAmount = totalItemAmount.subtract(totalDiscountedAmount);
        boolean hasDiscount = discountAmount.compareTo(BigDecimal.ZERO) > 0;
        
        // Build discount label from the actual applied discount record (avoid deriving percent from totals)
        String discountLabel = null;
        if (hasDiscount) {
            discountLabel = buildDiscountLabelFromDiscount(item, userLocale);
        }
        
        // Item main row (shows BASE amount only; modifiers shown as separate rows)
        html.append("<tr>");
        html.append("<td class='item-name'>").append(receiptUtil.escapeHtml(itemName)).append(HTML_CLOSE_TD);
        html.append("<td class='item-qty'>").append(itemQty).append(HTML_CLOSE_TD);
        html.append(HTML_OPEN_TD_PRICE).append(currencySymbol)
                .append(CurrencyFormatter.formatAmount(baseLineTotal, currencySymbol))
                .append(HTML_CLOSE_TD);
        html.append(HTML_CLOSE_TR);

        // Modifier rows (each on its own line, show modifier prices)
        appendItemModifierRows(html, item, itemQty, currencySymbol, userLocale, true);
        
        // Discount row if applicable
        if (hasDiscount && discountLabel != null) {
            html.append("<tr>");
            html.append("<td class='item-discount' colspan='2'>").append(receiptUtil.escapeHtml(discountLabel)).append(HTML_CLOSE_TD);
            html.append(HTML_OPEN_TD_PRICE).append("-").append(currencySymbol)
                    .append(CurrencyFormatter.formatAmount(discountAmount, currencySymbol))
                    .append(HTML_CLOSE_TD);
            html.append(HTML_CLOSE_TR);
        }
    }

    /**
     * Builds a localized discount label using the stored Discount record (type/value),
     * instead of deriving percent/flat from totals (which becomes inaccurate when modifiers are itemized).
     */
    private String buildDiscountLabelFromDiscount(OrderedItem item, Locale userLocale) {
        String discountText = messageUtil.getMessage("receipt.discount", userLocale);
        if (item == null || item.getDiscountId() == null) {
            return discountText;
        }
        try {
            Discount discount = discountRepository.findById(item.getDiscountId()).orElse(null);
            if (discount == null || discount.getDiscountType() == null) {
                return discountText;
            }
            if (discount.getDiscountType() == DiscountType.PERCENT) {
                BigDecimal v = discount.getValue() != null ? discount.getValue() : BigDecimal.ZERO;
                BigDecimal normalized = v.stripTrailingZeros();
                String pct = normalized.scale() <= 0 ? String.valueOf(normalized.intValue()) : normalized.toPlainString();
                return discountText + " (" + pct + "%)";
            }
            if (discount.getDiscountType() == DiscountType.FLAT) {
                return discountText + " (Flat)";
            }
            return discountText;
        } catch (Exception e) {
            log.trace("Failed to resolve discount label from discountId", e);
            return discountText;
        }
    }

    /**
     * Appends table rows for item modifiers associated with an ordered item.
     *
     * @param html        the StringBuilder to append rows to
     * @param item        the ordered item entity
     * @param userLocale  locale for localized text
     */
    private void appendItemModifierRows(StringBuilder html,
                                        OrderedItem item,
                                        int itemQty,
                                        String currencySymbol,
                                        Locale userLocale,
                                        boolean showPrice) {
        if (item.getOrderedItemModifiers() == null || item.getOrderedItemModifiers().isEmpty()) {
            return;
        }
        for (OrderedItemModifier mod : item.getOrderedItemModifiers()) {
            String modName = receiptUtil.getLocalizedName(mod.getModifierItem().getTranslations(), userLocale, "");
            if (modName == null || modName.isBlank()) {
                continue;
            }
            html.append("<tr>");
            html.append("<td class='item-mod'>+ ").append(receiptUtil.escapeHtml(modName)).append(HTML_CLOSE_TD);
            html.append(HTML_EMPTY_QTY_CELL);
            if (showPrice) {
                BigDecimal modUnitPrice = mod.getPrice() != null ? mod.getPrice() : BigDecimal.ZERO;
                BigDecimal modLineTotal = modUnitPrice.multiply(BigDecimal.valueOf(itemQty));
                html.append(HTML_OPEN_TD_PRICE).append("+").append(currencySymbol)
                        .append(CurrencyFormatter.formatAmount(modLineTotal, currencySymbol))
                        .append(HTML_CLOSE_TD);
            } else {
                html.append(HTML_OPEN_TD_PRICE).append(HTML_CLOSE_TD);
            }
            html.append(HTML_CLOSE_TR);
        }
    }

    /**
     * Appends table rows for ordered combos including child items.
     *
     * @param html          the StringBuilder to append rows to
     * @param order         the order entity containing combos
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale    locale for localized text
     * @param taxConfig     tax display configuration for alcoholic/non-alcoholic tax breakdown
     * @return true if any alcoholic items are found in combos, false otherwise
     */
    private boolean appendOrderedComboRows(StringBuilder html, Order order, String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
        if (order.getOrderedCombos() == null) {
            return false;
        }
        boolean hasAlcoholicItems = false;
        for (var oc : order.getOrderedCombos()) {
            if (oc.getItemStatus() == ItemStatus.CANCELED) {
                continue;
            }
            
            String comboName = getComboName(oc, userLocale);
            BigDecimal comboTotal = oc.getTotalComboAmount() != null ? oc.getTotalComboAmount() : BigDecimal.ZERO;
            
            // Combo main row
            html.append("<tr>");
            html.append("<td class='item-name'>").append(receiptUtil.escapeHtml(comboName)).append(HTML_CLOSE_TD);
            int comboQty = oc.getQuantity() != null ? oc.getQuantity() : 1;
            html.append("<td class='item-qty'>").append(comboQty).append(HTML_CLOSE_TD);
            html.append(HTML_OPEN_TD_PRICE).append(currencySymbol)
                    .append(CurrencyFormatter.formatAmount(comboTotal, currencySymbol))
                    .append(HTML_CLOSE_TD);
            html.append(HTML_CLOSE_TR);

            // Child items under combo - check if any are alcoholic
            boolean comboHasAlcoholic = appendComboChildItems(html, oc, currencySymbol, userLocale, taxConfig);
            if (comboHasAlcoholic) {
                hasAlcoholicItems = true;
            }
        }
        return hasAlcoholicItems;
    }

    private String getComboName(com.gulfnet.shared_library.entity.OrderedCombo oc, Locale userLocale) {
        try {
            return receiptUtil.getLocalizedName(oc.getCombo().getTranslations(), userLocale, "Combo");
        } catch (Exception e) {
            log.trace("Could not get localized combo name", e);
            return "Combo";
        }
    }

    /**
     * Appends table rows for child items within an ordered combo.
     *
     * @param html          the StringBuilder to append rows to
     * @param oc            the ordered combo entity
     * @param currencySymbol currency symbol for formatting amounts
     * @param userLocale    locale for localized text
     * @param taxConfig     tax display configuration for alcoholic/non-alcoholic tax breakdown
     * @return true if any alcoholic items are found in this combo, false otherwise
     */
    private boolean appendComboChildItems(StringBuilder html, com.gulfnet.shared_library.entity.OrderedCombo oc,
                                        String currencySymbol, Locale userLocale, TaxDisplayConfig taxConfig) {
        if (oc.getOrderedItems() == null || oc.getOrderedItems().isEmpty()) {
            return false;
        }
        boolean hasAlcoholicItems = false;
        for (OrderedItem ci : oc.getOrderedItems()) {
            String ciName = receiptUtil.getLocalizedName(ci.getItem().getTranslations(), userLocale, "");
            
            // Check if this item is alcoholic
            if (isAlcoholicItem(ci)) {
                hasAlcoholicItems = true;
            }
            
            // Add * marker for alcoholic items if tax rates differ
            if (taxConfig.useAlcoholMarker && isAlcoholicItem(ci)) {
                ciName = ciName + "*";
            }

            // Child item row (amount usually 0.00 as price is on combo)
            html.append("<tr>");
            html.append("<td class='item-sub'>- ").append(receiptUtil.escapeHtml(ciName)).append(HTML_CLOSE_TD);
            html.append(HTML_EMPTY_QTY_CELL);
            html.append(HTML_OPEN_TD_PRICE).append(currencySymbol)
                    .append(CurrencyFormatter.formatAmount(BigDecimal.ZERO, currencySymbol))
                    .append(HTML_CLOSE_TD);
            html.append(HTML_CLOSE_TR);

            // Modifiers for child item (price hidden in receipt)
            int ciQty = ci.getQuantity() != null ? ci.getQuantity() : 1;
            appendItemModifierRows(html, ci, ciQty, currencySymbol, userLocale, false);
        }
        return hasAlcoholicItems;
    }

    /**
     * Appends the totals section including subtotal, discounts, service charge, tax, and total amount.
     *
     * @param html              the StringBuilder to append totals to
     * @param order             the order entity
     * @param currencySymbol    currency symbol for formatting amounts
     * @param userLocale        locale for localized text
     * @param chain             restaurant chain configuration data
     * @param taxConfig         tax display configuration for alcoholic/non-alcoholic tax breakdown
     */
    private void appendTotals(StringBuilder html, Order order, String currencySymbol, Locale userLocale,
                              RestaurantChainConfigProperties.RestaurantChainData chain, TaxDisplayConfig taxConfig) {
        html.append("<div class='totals'>");

        appendSubtotalLine(html, order, currencySymbol, userLocale);
        appendCouponDiscountLine(html, order, currencySymbol, userLocale);
        appendServiceChargeLine(html, order, currencySymbol, userLocale, chain);
        appendPackingChargeLine(html, order, currencySymbol, userLocale, chain);
        appendConsumptionTaxBreakdownLines(html, order, currencySymbol, userLocale, taxConfig);
        appendAdditionalDiscountLine(html, order, currencySymbol, userLocale);
        appendTotalLine(html, order, currencySymbol, userLocale);

        html.append(HTML_CLOSE_DIV);
    }

    private void appendSubtotalLine(StringBuilder html, Order order, String currencySymbol, Locale userLocale) {
        if (order.getSubTotal() == null) {
            return;
        }
        String label = messageUtil.getMessage("receipt.refund.subtotal", userLocale);
        receiptUtil.appendTotalLine(html, label, order.getSubTotal(), currencySymbol, false, false);
    }

    private void appendCouponDiscountLine(StringBuilder html, Order order, String currencySymbol, Locale userLocale) {
        if (order.getDiscountAmount() == null || order.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (order.getDiscountCode() == null || order.getDiscountCode().isBlank()) {
            return;
        }
        String couponLabel = messageUtil.getMessage("receipt.coupon.applied", userLocale, "Coupon Applied");
        String label = couponLabel + " (" + receiptUtil.escapeHtml(order.getDiscountCode()) + ")";
        receiptUtil.appendTotalLine(html, label, order.getDiscountAmount(), currencySymbol, true, false);
    }

    private void appendServiceChargeLine(StringBuilder html,
                                         Order order,
                                         String currencySymbol,
                                         Locale userLocale,
                                         RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (order.getServiceChargeAmount() == null || order.getServiceChargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.refund.service.charge", userLocale);
        String label = baseLabel;
        if (chain != null && chain.getServiceChargesForDineIn() != null) {
            RestaurantChainConfigProperties.ServiceChargesForDineIn serviceCharge = chain.getServiceChargesForDineIn();
            label = buildChargeLabel(baseLabel, serviceCharge.getValue(), serviceCharge.getType(), currencySymbol, userLocale);
        }
        receiptUtil.appendTotalLine(html, label, order.getServiceChargeAmount(), currencySymbol, false, false);
    }

    private void appendPackingChargeLine(StringBuilder html,
                                         Order order,
                                         String currencySymbol,
                                         Locale userLocale,
                                         RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (order.getPackingChargeAmount() == null || order.getPackingChargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.packing.charge", userLocale);
        String label = baseLabel;
        if (chain != null && chain.getPackingChargesForTakeaway() != null) {
            RestaurantChainConfigProperties.PackingChargesForTakeaway packingCharge = chain.getPackingChargesForTakeaway();
            label = buildChargeLabel(baseLabel, packingCharge.getValue(), packingCharge.getType(), currencySymbol, userLocale);
        }
        receiptUtil.appendTotalLine(html, label, order.getPackingChargeAmount(), currencySymbol, false, false);
    }

    private record TaxLineLabels(String taxableMessageKey, String taxMessageKey, String taxableFallbackLabel, String taxFallbackLabel) {
    }

    private void appendConsumptionTaxBreakdownLines(StringBuilder html,
                                                    Order order,
                                                    String currencySymbol,
                                                    Locale userLocale,
                                                    TaxDisplayConfig taxConfig) {
        if (order.getTaxAmount() == null || order.getTaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal alcoholicTax = order.getAlcoholicTaxAmount() != null ? order.getAlcoholicTaxAmount() : BigDecimal.ZERO;
        BigDecimal nonAlcoholicTax = order.getNonAlcoholicTaxAmount() != null ? order.getNonAlcoholicTaxAmount() : BigDecimal.ZERO;

        BigDecimal alcoholicTaxable = resolveTaxableAmount(
                order.getAlcoholicTaxableAmount(),
                alcoholicTax,
                taxConfig != null ? taxConfig.alcoholicTaxCharge : null);
        BigDecimal nonAlcoholicTaxable = resolveTaxableAmount(
                order.getNonAlcoholicTaxableAmount(),
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

    private void appendAdditionalDiscountLine(StringBuilder html, Order order, String currencySymbol, Locale userLocale) {
        if (order.getAdditionalDiscountAmount() == null || order.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        StringBuilder addlLabel = new StringBuilder(messageUtil.getMessage("receipt.additional.discount", userLocale));
        if (order.getAdditionalDiscountType() != null && order.getAdditionalDiscountValue() != null
                && order.getAdditionalDiscountType().name().equals("PERCENT")) {
            addlLabel.append(" (").append(order.getAdditionalDiscountValue()).append("%)");
        }
        receiptUtil.appendTotalLine(html, addlLabel.toString(), order.getAdditionalDiscountAmount(), currencySymbol, true, false);
    }

    private void appendTotalLine(StringBuilder html, Order order, String currencySymbol, Locale userLocale) {
        String totalLabel = messageUtil.getMessage("receipt.total", userLocale);
        BigDecimal totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        receiptUtil.appendTotalLine(html, totalLabel, totalAmount, currencySymbol, false, true);
    }

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

    /**
     * Locale for receipt PDFs and receipt emails: chain {@code defaultLanguageCode} when configured;
     * otherwise request {@link LocaleContextHolder} then {@code en}.
     */
    public Locale receiptLocaleFromChainConfig() {
        String chainDefault = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getDefaultLanguageCode() : null;
        return Locale.forLanguageTag(DateTimeUtil.resolveReceiptDisplayLanguage(chainDefault, null));
    }

    /**
     * Sends a receipt email to the customer with order and transaction details.
     * 
     * @param email The recipient email address
     * @param order The order entity
     * @param transaction The transaction entity
     * @param locale The locale for localization
     */
    public void sendReceiptEmail(String email, Order order, Transaction transaction, Locale locale) {
        log.info("Starting receipt email sending process for email: '{}', order: '{}', transaction: '{}', locale: '{}'", 
                email, order.getId(), transaction.getId(), locale.getLanguage());
        
        String transactionReceiptUrl = null;
        
        try {
            // Validate email format
            if (!orderValidationService.isValidEmailFormat(email)) {
                log.warn("Invalid email format: '{}' - cannot send receipt email", email);
                return;
            }
            
            log.info("Email validation passed for: '{}'", email);
            
            // Get localized content
            log.info("Retrieving localized content for locale: '{}'", locale.getLanguage());
            var chain = restaurantChainConfigProperties.getChain();
            String currencySymbol = chain != null && chain.getCurrency() != null ? 
                                   chain.getCurrency() : 
                                   messageUtil.getMessage("receipt.currency.symbol", locale);
            String restaurantName = orderValidationService.getRestaurantName(order.getRestaurant(), locale);
            
            log.info("Retrieved currency symbol: '{}', restaurant name: '{}'", currencySymbol, restaurantName);
            
            String subject = messageUtil.getMessage("email.receipt.subject", locale, order.getOrderNumber());
            log.info("Generated email subject: '{}'", subject);
            
            // Fetch receipt URL directly from transaction table and convert to pre-signed URL
            transactionReceiptUrl = transaction.getReceiptUrl();
            log.info("Receipt URL from transaction table: {}", transactionReceiptUrl);
            
            String preSignedReceiptUrl = null;
            if (transactionReceiptUrl != null && !transactionReceiptUrl.trim().isEmpty()) {
                preSignedReceiptUrl = awsService.getPreSignedUrlForPdf(transactionReceiptUrl);
                log.info("Generated pre-signed URL for receipt PDF (with inline headers): {}", preSignedReceiptUrl);
            } else {
                log.warn("No receipt URL found in transaction table for transaction: {}", transaction.getId());
            }
            
            // Build HTML body
            String htmlBody = buildReceiptEmailHtml(restaurantName, order, transaction, currencySymbol, preSignedReceiptUrl, locale);
            
            log.info("HTML body generated successfully. Body length: {} characters", htmlBody.length());
            log.info("Receipt URL from transaction table used in email: '{}'", transactionReceiptUrl);
            
            // Check if EmailSender is available
            if (emailSender == null) {
                log.error("EmailSender is null - cannot send email to: '{}'", email);
                return;
            }
            
            log.info("Attempting to send email to: '{}' with subject: '{}'", email, subject);
            emailSender.sendEmail(email, subject, htmlBody);
            log.info("Receipt email sent successfully to: '{}'", email);
            
        } catch (Exception e) {
            log.error("Failed to send receipt email to '{}' for order '{}' and transaction '{}': {}", 
                    email, order.getId(), transaction.getId(), e.getMessage(), e);
            log.error("Email sending failure details - Email: '{}', Order: '{}', Transaction: '{}', Locale: '{}', ReceiptURL: '{}'", 
                    email, order.getId(), transaction.getId(), locale.getLanguage(), transactionReceiptUrl);
            // Don't fail the payment if email sending fails
        }
    }

    /**
     * Builds HTML content for receipt email with order and transaction details.
     *
     * @param restaurantName      the restaurant name
     * @param order               the order entity
     * @param transaction          the transaction entity
     * @param currencySymbol       currency symbol for formatting amounts
     * @param preSignedReceiptUrl  pre-signed URL for the receipt PDF
     * @param locale               locale for localized text
     * @return HTML string for the email body
     */
    private String buildReceiptEmailHtml(String restaurantName, Order order, Transaction transaction,
                                          String currencySymbol, String preSignedReceiptUrl, Locale locale) {
        Locale mailLocale = locale != null ? locale : Locale.ENGLISH;
        String lang = mailLocale.getLanguage();
        // Same localized date rules as PDF receipt (en / ja / th)
        String transactionDate = DateTimeUtil.format(LocalDateTime.now(resolveChainZoneId()), lang);
        String dateLabel = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.date", mailLocale));
        String orderNumberLabel = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.order.number", mailLocale));
        String paymentMethodLabel = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.payment.method", mailLocale));
        String paymentMethodValue = receiptUtil.escapeHtml(
                receiptUtil.getPaymentMethodDisplayName(transaction.getPaymentMethod(),
                        restaurantChainConfigProperties.getChain(),
                        mailLocale)
        );
        String thankYouTitle = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.thank.you", mailLocale));
        String thankYouTagline = receiptUtil.escapeHtml(messageUtil.getMessage("email.receipt.tagline", mailLocale));
        String htmlLang = mailLocale.toLanguageTag().isBlank() ? "en" : mailLocale.toLanguageTag();

        String cashierEmailRow = "";
        String cashierDisplay = resolveCashierDisplayName(transaction, lang);
        if (cashierDisplay != null && !cashierDisplay.isBlank()) {
            String cashierLabel = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.cashier", mailLocale));
                String cashierEsc = receiptUtil.escapeHtml(cashierDisplay);
                cashierEmailRow =
                        "                                            <tr>\n" +
                        "                                                <td colspan=\"2\" style=\"padding-top: 14px; border-top: 1px solid #e5e7eb;\">\n" +
                        "                                                    <p style=\"margin: 0 0 4px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px;\">\n" +
                        "                                                        " + cashierLabel + "\n" +
                        "                                                    </p>\n" +
                        "                                                    <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: #374151;\">\n" +
                        "                                                        " + cashierEsc + "\n" +
                        "                                                    </p>\n" +
                        "                                                </td>\n" +
                        "                                            </tr>\n";
        }

        // Get restaurant address and phone (optional)
        String restaurantAddress = "";
        String phoneSection = "";
        if (order.getRestaurant() != null) {
            Restaurant restaurant = order.getRestaurant();
            AddressDto addressDto = AddressDto.fromRestaurant(restaurant, chainCountryName());
            String formatted = AddressFormatter.format(addressDto, mailLocale);
            if (!formatted.isEmpty()) {
                restaurantAddress = receiptUtil.escapeHtml(formatted).replace("\n", "<br>");
            }
            if (restaurant.getPhoneNumber() != null && !restaurant.getPhoneNumber().trim().isEmpty()) {
                String phoneLabel = receiptUtil.escapeHtml(messageUtil.getMessage("receipt.phone", mailLocale));
                String phoneValue = receiptUtil.escapeHtml(restaurant.getPhoneNumber().trim());
                phoneSection =
                        "                                        <p style=\"margin: 8px 0 0 0; font-size: 12px; line-height: 16px; color: #6b7280; font-weight: 400;\">\n" +
                        "                                            <strong style=\"color: #374151; font-weight: 600;\">" + phoneLabel + HTML_CLOSE_STRONG_SPACE + phoneValue + "\n" +
                        EMAIL_P_CLOSE_40;
            }
        }
        
        // Fetch ordered items and combos for email display
        List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
        List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(order.getId());
        
        // Determine tax display configuration
        var chain = restaurantChainConfigProperties.getChain();
        TaxDisplayConfig taxConfig = determineTaxDisplayConfig(order, chain);
        
        // Check if there are any alcoholic items
        boolean hasAlcoholicItems = checkForAlcoholicItems(orderedItems, order);
        
        // Build items table HTML for email
        String itemsTableHtml = buildEmailItemsTable(orderedItems, orderedCombos, currencySymbol, mailLocale, taxConfig, hasAlcoholicItems);
        
        // Only show some separators when the alcoholic marker legend is visible.
        boolean showAlcoholLegend = taxConfig.useAlcoholMarker && hasAlcoholicItems;
        // Build financial summary HTML for email
        String financialSummaryHtml = buildEmailFinancialSummary(order, currencySymbol, mailLocale, chain, taxConfig, showAlcoholLegend);
        
        // Build receipt section (button intentionally removed)
        // If receipt URL is missing, we still show "receipt.not.available" message.
        String receiptSection;
        if (preSignedReceiptUrl != null && !preSignedReceiptUrl.trim().isEmpty()) {
            receiptSection = "";
        } else {
            receiptSection =
                    EMAIL_TR_OPEN_20 +
                    "                        <td style=\"padding: 32px 32px 0 32px;\">\n" +
                    EMAIL_TABLE_PRES_100 +
                    "                                <tr>\n" +
                    "                                    <td align=\"center\" style=\"padding: 16px 0;\">\n" +
                    "                                        <p style=\"margin: 0; font-size: 14px; line-height: 20px; color: #6b7280; font-style: italic;\">\n" +
                    EMAIL_INDENT_44 + messageUtil.getMessage("receipt.not.available", mailLocale) + "\n" +
                    EMAIL_P_CLOSE_40 +
                    EMAIL_TD_CLOSE_36 +
                    "                                </tr>\n" +
                    EMAIL_TABLE_CLOSE_28 +
                    EMAIL_TD_CLOSE_24 +
                    EMAIL_TR_CLOSE_20;
        }

        // Logo URL for email header (must be publicly accessible / pre-signed for email clients)
        String logoImgTag;
        Restaurant restaurantForLogo = order.getRestaurant();
        String signedLogoUrl = "";
        if (restaurantForLogo != null
                && restaurantForLogo.getLogoUrl() != null
                && !restaurantForLogo.getLogoUrl().trim().isEmpty()) {
            signedLogoUrl = awsService.getPreSignedUrl(restaurantForLogo.getLogoUrl());
        }

        String safeLogoUrl = signedLogoUrl != null
                && !signedLogoUrl.trim().isEmpty()
                && !signedLogoUrl.equalsIgnoreCase("location")
                ? signedLogoUrl.replace("'", "&#39;").replace("\"", "&quot;")
                : "";

        if (!safeLogoUrl.isEmpty()) {
            logoImgTag = "<img src=\"" + safeLogoUrl + "\" "
                    + "alt=\"" + receiptUtil.escapeHtml(restaurantName != null ? restaurantName : "") + " Logo\" "
                    + "width=\"64\" height=\"64\" "
                    + "style=\"display: block; width: 64px; height: 64px; border-radius: 50%; object-fit: cover;\" />";
        } else {
            // Fallback placeholder if restaurant logo is missing/unavailable.
            logoImgTag = "<img src=\"https://via.placeholder.com/64x64/f3f4f6/9ca3af?text=LOGO\" "
                    + "alt=\"" + receiptUtil.escapeHtml(restaurantName != null ? restaurantName : "") + " Logo\" "
                    + "width=\"64\" height=\"64\" "
                    + "style=\"display: block; width: 64px; height: 64px; border-radius: 50%; object-fit: cover;\" />";
        }
        
        // Build the complete HTML email
        return "<!DOCTYPE html>\n" +
                "<html lang=\"" + receiptUtil.escapeHtml(htmlLang) + "\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\n" +
                "    <title>Your Restaurant Receipt</title>\n" +
                "    <!--[if mso]>\n" +
                "    <style type=\"text/css\">\n" +
                "        body, table, td, a { font-family: Arial, sans-serif !important; }\n" +
                "        .logo-container { width: 64px !important; height: 64px !important; }\n" +
                "    </style>\n" +
                "    <![endif]-->\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 16px 0; background-color: #f9fafb; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased;\">\n" +
                "    \n" +
                "    <!-- Preheader Text (Hidden preview text) -->\n" +
                "    <div style=\"display: none; max-height: 0; overflow: hidden; font-size: 1px; line-height: 1px; color: transparent;\">\n" +
                "        Receipt for Order #" + receiptUtil.escapeHtml(order.getOrderNumber() != null ? order.getOrderNumber() : "") + " · Total: " + receiptUtil.escapeHtml(currencySymbol != null ? currencySymbol : "") + (order.getTotalAmount() != null ? order.getTotalAmount().toString() : "0.00") + " · Payment: " + paymentMethodValue + "\n" +
                "    </div>\n" +
                "    \n" +
                "    <!-- Main Email Container -->\n" +
                "    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color: #f9fafb;\">\n" +
                "        <tr>\n" +
                "            <td align=\"center\" style=\"padding: 0;\">\n" +
                "                \n" +
                "                <!-- Email Content Container (600px max width) -->\n" +
                "                <table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" \n" +
                "                       style=\"width: 100%; max-width: 600px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); border: 1px solid #e5e7eb;\">\n" +
                "                    \n" +
                "                    <!-- Header Section -->\n" +
                EMAIL_TR_OPEN_20 +
                "                        <td style=\"padding: 32px 32px 24px 32px; text-align: center; border-bottom: 1px solid #e5e7eb; background-color: #ffffff;\">\n" +
                "                            <!-- Logo Placeholder -->\n" +
                "                            <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" style=\"margin: 0 auto 16px auto;\">\n" +
                "                                <tr>\n" +
                "                                    <td align=\"center\">\n" +
                "                                        <div style=\"width: 64px; height: 64px; background-color: #f3f4f6; border-radius: 50%; display: inline-block; overflow: hidden; vertical-align: middle;\">\n" +
                "                                            " + logoImgTag + "\n" +
                "                                        </div>\n" +
                EMAIL_TD_CLOSE_36 +
                "                                </tr>\n" +
                EMAIL_TABLE_CLOSE_28 +
                "                            \n" +
                "                            <!-- Restaurant Name / Thank You Message -->\n" +
                "                            <h1 style=\"margin: 0 0 4px 0; font-size: 24px; line-height: 32px; font-weight: 700; color: #111827; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\">\n" +
                "                                " + thankYouTitle + "\n" +
                "                            </h1>\n" +
                "                            <p style=\"margin: 0; font-size: 12px; line-height: 16px; color: #6b7280; font-weight: 400; text-transform: uppercase; letter-spacing: 0.5px;\">\n" +
                "                                " + thankYouTagline + "\n" +
                EMAIL_P_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                    \n" +
                "                    <!-- Transaction Details Section -->\n" +
                EMAIL_TR_OPEN_20 +
                "                        <td style=\"padding: 24px 32px; background-color: #f9fafb; border-bottom: 1px solid #e5e7eb;\">\n" +
                EMAIL_TABLE_PRES_100 +
                "                                <tr>\n" +
                "                                    <!-- Transaction Date (Left) -->\n" +
                "                                    <td style=\"padding: 0; vertical-align: top;\">\n" +
                "                                        <p style=\"margin: 0 0 4px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px;\">\n" +
                "                                            " + dateLabel + "\n" +
                EMAIL_P_CLOSE_40 +
                "                                        <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: #111827;\">\n" +
                "                                            " + transactionDate + "\n" +
                EMAIL_P_CLOSE_40 +
                EMAIL_TD_CLOSE_36 +
                "                                    <!-- Order Number (Right) -->\n" +
                "                                    <td align=\"right\" style=\"padding: 0; vertical-align: top;\">\n" +
                "                                        <p style=\"margin: 0 0 4px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px;\">\n" +
                "                                            " + orderNumberLabel + "\n" +
                EMAIL_P_CLOSE_40 +
                "                                        <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: #111827;\">\n" +
                "                                            #" + receiptUtil.escapeHtml(order.getOrderNumber() != null ? order.getOrderNumber() : "") + "\n" +
                EMAIL_P_CLOSE_40 +
                EMAIL_TD_CLOSE_36 +
                "                                </tr>\n" +
                EMAIL_TABLE_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                    \n" +
                "                    <!-- Itemized List Section -->\n" +
                itemsTableHtml +
                "                    \n" +
                "                    <!-- Financial Summary Section -->\n" +
                financialSummaryHtml +
                "                    \n" +
                "                    <!-- Order Details Section -->\n" +
                EMAIL_TR_OPEN_20 +
                EMAIL_TD_PAD_24_32_TOP +
                EMAIL_TABLE_PRES_100 +
                "                                \n" +
                EMAIL_TABLE_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                    \n" +
                "                    <!-- Payment Method Section -->\n" +
                EMAIL_TR_OPEN_20 +
                EMAIL_TD_PAD_24_32_TOP +
                "                            <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" \n" +
                "                                   style=\"background-color: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px;\">\n" +
                "                                <tr>\n" +
                "                                    <td style=\"padding: 16px;\">\n" +
                "                                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n" +
                "                                            <tr>\n" +
                "                                                <!-- Payment Icon and Method (Left) -->\n" +
                "                                                <td style=\"padding: 0; vertical-align: middle;\">\n" +
                "                                                    <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n" +
                "                                                        <tr>\n" +
                "                                                            <td style=\"padding-right: 12px; vertical-align: middle;\">\n" +
                "                                                                <!-- Card Icon SVG (inline) -->\n" +
                "                                                                <svg width=\"32\" height=\"32\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\" style=\"display: block;\">\n" +
                "                                                                    <path d=\"M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z\" fill=\"#9ca3af\"/>\n" +
                "                                                                </svg>\n" +
                "                                                            </td>\n" +
                "                                                            <td style=\"vertical-align: middle;\">\n" +
                "                                                                <p style=\"margin: 0 0 2px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px;\">\n" +
                "                                                    " + paymentMethodLabel + "\n" +
                "                                                </p>\n" +
                "                                                                <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: #374151;\">\n" +
                "                                                                    " + paymentMethodValue + "\n" +
                "                                                                </p>\n" +
                "                                                            </td>\n" +
                "                                                        </tr>\n" +
                "                                                    </table>\n" +
                "                                                </td>\n" +
                "\n" +
                "                                                <!-- Transaction Number (Right) -->\n" +
                "                                                <td align=\"right\" style=\"padding-left: 16px; vertical-align: middle;\">\n" +
                "                                                    <p style=\"margin: 0 0 2px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px;\">\n" +
                "                                                        " + messageUtil.getMessage("receipt.transaction.number", mailLocale, "") + "\n" +
                "                                                    </p>\n" +
                "                                                    <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: #374151;\">\n" +
                "                                                        " + receiptUtil.escapeHtml(transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "") + "\n" +
                "                                                    </p>\n" +
                "                                                </td>\n" +
                "                                            </tr>\n" +
                cashierEmailRow +
                "                                        </table>\n" +
                EMAIL_TD_CLOSE_36 +
                "                                </tr>\n" +
                EMAIL_TABLE_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                    \n" +
                receiptSection +
                "                    \n" +
                "                    <!-- Restaurant Contact Info -->\n" +
                EMAIL_TR_OPEN_20 +
                "                        <td style=\"padding: 32px 32px 24px 32px;\">\n" +
                EMAIL_TABLE_PRES_100 +
                "                                <tr>\n" +
                "                                    <td align=\"center\" style=\"padding: 0;\">\n" +
                "                                        <p style=\"margin: 0 0 4px 0; font-size: 14px; line-height: 20px; font-weight: 700; color: #111827;\">\n" +
                "                                            " + receiptUtil.escapeHtml(restaurantName != null ? restaurantName : "") + "\n" +
                EMAIL_P_CLOSE_40 +
                "                                        <p style=\"margin: 0; font-size: 12px; line-height: 16px; color: #6b7280; font-weight: 400;\">\n" +
                "                                            " + restaurantAddress + "\n" +
                EMAIL_P_CLOSE_40 +
                phoneSection +
                EMAIL_TD_CLOSE_36 +
                "                                </tr>\n" +
                EMAIL_TABLE_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                    \n" +
                "                </table>\n" +
                "                <!-- End Email Content Container -->\n" +
                "                \n" +
                "                <!-- Footer Legal Text -->\n" +
                "                <table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" \n" +
                "                       style=\"width: 100%; max-width: 600px; margin: 32px auto 0 auto;\">\n" +
                EMAIL_TR_OPEN_20 +
                "                        <td align=\"center\" style=\"padding: 0 32px;\">\n" +
                "                            <p style=\"margin: 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 400; text-align: center;\">\n" +
                "                                You are receiving this email because you opted for a digital receipt at checkout.\n" +
                "                                <br/>\n" +
                "                                © " + LocalDate.now().getYear() + " " + receiptUtil.escapeHtml(restaurantName != null ? restaurantName : "") + ". All rights reserved.\n" +
                EMAIL_P_CLOSE_28 +
                EMAIL_TD_CLOSE_24 +
                EMAIL_TR_CLOSE_20 +
                "                </table>\n" +
                "                \n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "    <!-- End Main Email Container -->\n" +
                "    \n" +
                "</body>\n" +
                "</html>";
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
     * Builds the items table HTML for email receipt.
     */
    private String buildEmailItemsTable(List<OrderedItem> orderedItems, List<OrderedCombo> orderedCombos,
                                        String currencySymbol, Locale locale, TaxDisplayConfig taxConfig,
                                        boolean hasAlcoholicItems) {
        StringBuilder html = new StringBuilder();
        appendEmailItemsTableHeader(html, locale);
        appendEmailOrderedItemRows(html, orderedItems, currencySymbol, locale, taxConfig);
        appendEmailComboRows(html, orderedCombos, currencySymbol, locale, taxConfig);
        appendEmailItemsTableFooter(html, locale, taxConfig, hasAlcoholicItems);

        return html.toString();
    }

    private void appendEmailItemsTableHeader(StringBuilder html, Locale locale) {
        html.append(EMAIL_TR_OPEN_20);
        html.append(EMAIL_TD_PAD_24_32_TOP);
        html.append("                            <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"border-collapse: collapse;\">\n");
        html.append("                                <thead>\n");
        html.append("                                    <tr style=\"border-bottom: 1px solid #e5e7eb;\">\n");
        html.append("                                        <th align=\"left\" style=\"padding: 0 0 12px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px;\">\n");
        html.append(EMAIL_INDENT_44).append(messageUtil.getMessage("receipt.item", locale)).append("\n");
        html.append(EMAIL_CLOSE_TH_LINE);
        html.append("                                        <th align=\"right\" style=\"padding: 0 0 12px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px;\">\n");
        html.append(EMAIL_INDENT_44).append(messageUtil.getMessage("receipt.quantity", locale)).append("\n");
        html.append(EMAIL_CLOSE_TH_LINE);
        html.append("                                        <th align=\"right\" style=\"padding: 0 0 12px 0; font-size: 11px; line-height: 16px; color: #9ca3af; font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px;\">\n");
        html.append(EMAIL_INDENT_44).append(messageUtil.getMessage("receipt.price", locale)).append("\n");
        html.append(EMAIL_CLOSE_TH_LINE);
        html.append(EMAIL_CLOSE_TR_LINE);
        html.append("                                </thead>\n");
        html.append("                                <tbody>\n");
    }

    private void appendEmailOrderedItemRows(StringBuilder html,
                                           List<OrderedItem> orderedItems,
                                           String currencySymbol,
                                           Locale locale,
                                           TaxDisplayConfig taxConfig) {
        if (orderedItems == null || orderedItems.isEmpty()) {
            return;
        }
        for (OrderedItem item : orderedItems) {
            if (isComboChildItem(item) || item.getItemStatus() == ItemStatus.CANCELED) {
                continue;
            }
            appendEmailOrderedItemRow(html, item, currencySymbol, locale, taxConfig);
        }
    }

    private void appendEmailOrderedItemRow(StringBuilder html,
                                          OrderedItem item,
                                          String currencySymbol,
                                          Locale locale,
                                          TaxDisplayConfig taxConfig) {
        String itemName = receiptUtil.getLocalizedName(item.getItem().getTranslations(), locale, "Unknown Item");
        if (taxConfig.useAlcoholMarker && isAlcoholicItem(item)) {
            itemName = itemName + "*";
        }
        int qty = item.getQuantity() != null ? item.getQuantity() : 1;
        BigDecimal baseUnitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        BigDecimal baseLineTotal = baseUnitPrice.multiply(BigDecimal.valueOf(qty));

        html.append("                                    <tr style=\"border-bottom: 1px solid #f3f4f6;\">\n");
        html.append("                                        <td align=\"left\" style=\"padding: 16px 0;\">\n");
        html.append("                                            <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 500; color: #111827;\">\n");
        html.append("                                                ").append(receiptUtil.escapeHtml(itemName)).append("\n");
        html.append(EMAIL_CLOSE_P_LINE);
        appendEmailItemModifiers(html, item, qty, currencySymbol, locale, true);
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_OPEN_RIGHT_TD_PADDED_16_LINE);
        html.append(EMAIL_INDENT_44).append(qty).append("\n");
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_OPEN_RIGHT_TD_PADDED_16_LINE);
        html.append(EMAIL_INDENT_44).append(currencySymbol).append(CurrencyFormatter.formatAmount(baseLineTotal, currencySymbol)).append("\n");
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_CLOSE_TR_LINE);
    }

    private void appendEmailItemModifiers(StringBuilder html,
                                         OrderedItem item,
                                         int itemQty,
                                         String currencySymbol,
                                         Locale locale,
                                         boolean showPrice) {
        if (item.getOrderedItemModifiers() == null || item.getOrderedItemModifiers().isEmpty()) {
            return;
        }
        for (OrderedItemModifier mod : item.getOrderedItemModifiers()) {
            String modName = receiptUtil.getLocalizedName(mod.getModifierItem().getTranslations(), locale, "");
            if (modName == null || modName.isBlank()) {
                continue;
            }
            html.append("                                            <p style=\"margin: 4px 0 0 0; font-size: 12px; line-height: 16px; color: #6b7280;\">\n");
            if (showPrice) {
                BigDecimal modUnitPrice = mod.getPrice() != null ? mod.getPrice() : BigDecimal.ZERO;
                BigDecimal modLineTotal = modUnitPrice.multiply(BigDecimal.valueOf(itemQty));
                html.append("                                                + ")
                        .append(receiptUtil.escapeHtml(modName))
                        .append("  +")
                        .append(currencySymbol)
                        .append(CurrencyFormatter.formatAmount(modLineTotal, currencySymbol))
                        .append("\n");
            } else {
                html.append("                                                + ").append(receiptUtil.escapeHtml(modName)).append("\n");
            }
            html.append(EMAIL_CLOSE_P_LINE);
        }
    }

    private void appendEmailComboRows(StringBuilder html,
                                     List<OrderedCombo> orderedCombos,
                                     String currencySymbol,
                                     Locale locale,
                                     TaxDisplayConfig taxConfig) {
        if (orderedCombos == null || orderedCombos.isEmpty()) {
            return;
        }
        for (OrderedCombo oc : orderedCombos) {
            if (oc.getItemStatus() == ItemStatus.CANCELED) {
                continue;
            }
            appendEmailComboRow(html, oc, currencySymbol, locale, taxConfig);
        }
    }

    private void appendEmailComboRow(StringBuilder html,
                                    OrderedCombo oc,
                                    String currencySymbol,
                                    Locale locale,
                                    TaxDisplayConfig taxConfig) {
        String comboName = getComboName(oc, locale);
        BigDecimal comboTotal = oc.getTotalComboAmount() != null ? oc.getTotalComboAmount() : BigDecimal.ZERO;
        int comboQty = oc.getQuantity() != null ? oc.getQuantity() : 1;

        html.append("                                    <tr style=\"border-bottom: 1px solid #f3f4f6;\">\n");
        html.append("                                        <td align=\"left\" style=\"padding: 16px 0;\">\n");
        html.append("                                            <p style=\"margin: 0; font-size: 14px; line-height: 20px; font-weight: 500; color: #111827;\">\n");
        html.append("                                                ").append(receiptUtil.escapeHtml(comboName)).append("\n");
        html.append(EMAIL_CLOSE_P_LINE);
        appendEmailComboChildItems(html, oc, currencySymbol, locale, taxConfig);
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_OPEN_RIGHT_TD_PADDED_16_LINE);
        html.append(EMAIL_INDENT_44).append(comboQty).append("\n");
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_OPEN_RIGHT_TD_PADDED_16_LINE);
        html.append(EMAIL_INDENT_44).append(currencySymbol).append(CurrencyFormatter.formatAmount(comboTotal, currencySymbol)).append("\n");
        html.append(EMAIL_CLOSE_TD_LINE);
        html.append(EMAIL_CLOSE_TR_LINE);
    }

    private void appendEmailComboChildItems(StringBuilder html, OrderedCombo oc, String currencySymbol, Locale locale, TaxDisplayConfig taxConfig) {
        if (oc.getOrderedItems() == null || oc.getOrderedItems().isEmpty()) {
            return;
        }
        for (OrderedItem ci : oc.getOrderedItems()) {
            String ciName = receiptUtil.getLocalizedName(ci.getItem().getTranslations(), locale, "");
            if (taxConfig.useAlcoholMarker && isAlcoholicItem(ci)) {
                ciName = ciName + "*";
            }
            html.append("                                            <p style=\"margin: 4px 0 0 0; font-size: 12px; line-height: 16px; color: #6b7280;\">\n");
            html.append("                                                - ").append(receiptUtil.escapeHtml(ciName)).append("\n");
            html.append(EMAIL_CLOSE_P_LINE);

            // For combo child items, keep modifiers listed but without prices to avoid looking like double-charging
            int ciQty = ci.getQuantity() != null ? ci.getQuantity() : 1;
            appendEmailItemModifiers(html, ci, ciQty, currencySymbol, locale, false);
        }
    }

    private void appendEmailItemsTableFooter(StringBuilder html, Locale locale, TaxDisplayConfig taxConfig, boolean hasAlcoholicItems) {
        html.append("                                </tbody>\n");
        html.append(EMAIL_TABLE_CLOSE_28);

        // Legend explains what the * marker means; show it only when the marker is actually used.
        if (taxConfig.useAlcoholMarker && hasAlcoholicItems) {
            html.append("                            <p style=\"margin: 12px 0 0 0; font-size: 11px; line-height: 16px; color: #9ca3af;\">\n");
            html.append("                                * ").append(messageUtil.getMessage("receipt.alcoholic.item.indicator", locale, "Indicates alcoholic item")).append("\n");
            html.append(EMAIL_P_CLOSE_28);
        }

        html.append(EMAIL_TD_CLOSE_24);
        html.append(EMAIL_TR_CLOSE_20);
    }
    
    /**
     * Builds the financial summary HTML for email receipt.
     */
    private String buildEmailFinancialSummary(Order order, String currencySymbol, Locale locale,
                                             RestaurantChainConfigProperties.RestaurantChainData chain,
                                             TaxDisplayConfig taxConfig,
                                             boolean showAlcoholLegend) {
        StringBuilder html = new StringBuilder();
        appendEmailFinancialSummaryContainerStart(html, showAlcoholLegend);
        appendEmailFinancialSubtotal(html, order, currencySymbol, locale);
        appendEmailFinancialCouponDiscount(html, order, currencySymbol, locale);
        appendEmailFinancialServiceCharge(html, order, currencySymbol, locale, chain);
        appendEmailFinancialPackingCharge(html, order, currencySymbol, locale, chain);
        appendEmailFinancialTaxBreakdown(html, order, currencySymbol, locale, taxConfig);
        appendEmailFinancialAdditionalDiscount(html, order, currencySymbol, locale);
        appendEmailFinancialTotal(html, order, currencySymbol, locale);
        appendEmailFinancialSummaryContainerEnd(html);

        return html.toString();
    }

    private void appendEmailFinancialSummaryContainerStart(StringBuilder html, boolean showAlcoholLegend) {
        html.append(EMAIL_TR_OPEN_20);
        html.append(EMAIL_TD_PAD_24_32_TOP);
        html.append(EMAIL_TABLE_PRES_100);
        html.append("                                <tr>\n");
        html.append("                                    <td style=\"padding: 0;\">\n");
        if (showAlcoholLegend) {
            html.append("                                        <div style=\"border-top: 1px solid #e5e7eb; padding-top: 16px;\">\n");
        } else {
            html.append("                                        <div style=\"padding-top: 16px;\">\n");
        }
    }

    private void appendEmailFinancialSummaryContainerEnd(StringBuilder html) {
        html.append("                                        </div>\n");
        html.append(EMAIL_TD_CLOSE_36);
        html.append("                                </tr>\n");
        html.append(EMAIL_TABLE_CLOSE_28);
        html.append(EMAIL_TD_CLOSE_24);
        html.append(EMAIL_TR_CLOSE_20);
    }

    private void appendEmailFinancialSubtotal(StringBuilder html, Order order, String currencySymbol, Locale locale) {
        if (order.getSubTotal() == null || order.getSubTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        appendEmailFinancialPaddedTableRow(html,
                messageUtil.getMessage("receipt.refund.subtotal", locale),
                currencySymbol + CurrencyFormatter.formatAmount(order.getSubTotal(), currencySymbol));
    }

    private void appendEmailFinancialCouponDiscount(StringBuilder html, Order order, String currencySymbol, Locale locale) {
        if (order.getDiscountAmount() == null || order.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (order.getDiscountCode() == null || order.getDiscountCode().isBlank()) {
            return;
        }
        String couponLabel = messageUtil.getMessage("receipt.coupon.applied", locale, "Coupon Applied");
        String label = couponLabel + " (" + receiptUtil.escapeHtml(order.getDiscountCode()) + ")";
        // Use a simple 2-column table row for email client alignment (more reliable than flexbox).
        appendEmailFinancialPaddedTableRow(
                html,
                receiptUtil.escapeHtml(label),
                "-" + currencySymbol + CurrencyFormatter.formatAmount(order.getDiscountAmount(), currencySymbol));
    }

    private void appendEmailFinancialServiceCharge(StringBuilder html,
                                                   Order order,
                                                   String currencySymbol,
                                                   Locale locale,
                                                   RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (order.getServiceChargeAmount() == null || order.getServiceChargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.refund.service.charge", locale);
        String label = baseLabel;
        if (chain != null && chain.getServiceChargesForDineIn() != null) {
            RestaurantChainConfigProperties.ServiceChargesForDineIn serviceCharge = chain.getServiceChargesForDineIn();
            label = buildChargeLabel(baseLabel, serviceCharge.getValue(), serviceCharge.getType(), currencySymbol, locale);
        }
        appendEmailFinancialPaddedTableRow(html,
                receiptUtil.escapeHtml(label),
                currencySymbol + CurrencyFormatter.formatAmount(order.getServiceChargeAmount(), currencySymbol));
    }

    private void appendEmailFinancialPackingCharge(StringBuilder html,
                                                   Order order,
                                                   String currencySymbol,
                                                   Locale locale,
                                                   RestaurantChainConfigProperties.RestaurantChainData chain) {
        if (order.getPackingChargeAmount() == null || order.getPackingChargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String baseLabel = messageUtil.getMessage("receipt.packing.charge", locale);
        String label = baseLabel;
        if (chain != null && chain.getPackingChargesForTakeaway() != null) {
            RestaurantChainConfigProperties.PackingChargesForTakeaway packingCharge = chain.getPackingChargesForTakeaway();
            label = buildChargeLabel(baseLabel, packingCharge.getValue(), packingCharge.getType(), currencySymbol, locale);
        }
        appendEmailFinancialPaddedTableRow(html,
                receiptUtil.escapeHtml(label),
                currencySymbol + CurrencyFormatter.formatAmount(order.getPackingChargeAmount(), currencySymbol));
    }

    private void appendEmailFinancialTaxBreakdown(StringBuilder html,
                                                  Order order,
                                                  String currencySymbol,
                                                  Locale locale,
                                                  TaxDisplayConfig taxConfig) {
        if (order.getTaxAmount() == null || order.getTaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal alcoholicTax = order.getAlcoholicTaxAmount() != null ? order.getAlcoholicTaxAmount() : BigDecimal.ZERO;
        BigDecimal nonAlcoholicTax = order.getNonAlcoholicTaxAmount() != null ? order.getNonAlcoholicTaxAmount() : BigDecimal.ZERO;

        BigDecimal alcoholicTaxable = resolveTaxableAmount(
                order.getAlcoholicTaxableAmount(),
                alcoholicTax,
                taxConfig != null ? taxConfig.alcoholicTaxCharge : null);
        BigDecimal nonAlcoholicTaxable = resolveTaxableAmount(
                order.getNonAlcoholicTaxableAmount(),
                nonAlcoholicTax,
                taxConfig != null ? taxConfig.nonAlcoholicTaxCharge : null);

        boolean hasAlcoholicTaxableRow = alcoholicTaxable != null && alcoholicTaxable.compareTo(BigDecimal.ZERO) > 0;
        boolean hasAlcoholicTaxRow = alcoholicTax.compareTo(BigDecimal.ZERO) > 0;
        boolean hasNonAlcoholicTaxableRow = nonAlcoholicTaxable != null && nonAlcoholicTaxable.compareTo(BigDecimal.ZERO) > 0;
        boolean hasNonAlcoholicTaxRow = nonAlcoholicTax.compareTo(BigDecimal.ZERO) > 0;

        if (!(hasAlcoholicTaxableRow || hasAlcoholicTaxRow || hasNonAlcoholicTaxableRow || hasNonAlcoholicTaxRow)) {
            return;
        }

        html.append("                                            <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"padding: 8px 0;\">\n");
        appendEmailTaxRowIfPresent(html, currencySymbol, locale, alcoholicTaxable, taxConfig != null ? taxConfig.alcoholicTaxCharge : null,
                MSG_RECEIPT_ALCOHOLIC_TAXABLE_AMOUNT, "Alcoholic Taxable Amount");
        appendEmailTaxRowIfPresent(html, currencySymbol, locale, alcoholicTax, taxConfig != null ? taxConfig.alcoholicTaxCharge : null,
                MSG_RECEIPT_ALCOHOLIC_ITEM_TAX, "Alcoholic Tax");
        appendEmailTaxRowIfPresent(html, currencySymbol, locale, nonAlcoholicTaxable, taxConfig != null ? taxConfig.nonAlcoholicTaxCharge : null,
                MSG_RECEIPT_NON_ALCOHOLIC_TAXABLE_AMOUNT, "Non-Alcoholic Taxable Amount");
        appendEmailTaxRowIfPresent(html, currencySymbol, locale, nonAlcoholicTax, taxConfig != null ? taxConfig.nonAlcoholicTaxCharge : null,
                MSG_RECEIPT_NON_ALCOHOLIC_ITEM_TAX, "Non-Alcoholic Tax");
        html.append("                                            </table>\n");
    }

    private void appendEmailTaxRowIfPresent(StringBuilder html,
                                           String currencySymbol,
                                           Locale locale,
                                           BigDecimal amount,
                                           RestaurantChainConfigProperties.TaxSetup.TaxCharge taxCharge,
                                           String messageKey,
                                           String fallbackLabel) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String label;
        if (isJapanese(locale)) {
            String rateArg = formatPercentArg(taxCharge);
            label = rateArg != null
                    ? messageUtil.getMessage(messageKey, locale, rateArg)
                    : messageUtil.getMessage(messageKey, locale);
        } else if (messageKey.equals(MSG_RECEIPT_ALCOHOLIC_ITEM_TAX) || messageKey.equals(MSG_RECEIPT_NON_ALCOHOLIC_ITEM_TAX)) {
            String baseLabel = messageUtil.getMessage(messageKey, locale, fallbackLabel);
            label = taxCharge != null ? buildChargeLabel(baseLabel, taxCharge.getValue(), taxCharge.getType(), currencySymbol, locale) : baseLabel;
        } else {
            label = messageUtil.getMessage(messageKey, locale, fallbackLabel);
        }
        appendEmailFinancialRowInsideOpenTable(html,
                receiptUtil.escapeHtml(label),
                currencySymbol + CurrencyFormatter.formatAmount(amount, currencySymbol));
    }

    private void appendEmailFinancialAdditionalDiscount(StringBuilder html, Order order, String currencySymbol, Locale locale) {
        if (order.getAdditionalDiscountAmount() == null || order.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        StringBuilder addlLabel = new StringBuilder(messageUtil.getMessage("receipt.additional.discount", locale));
        if (order.getAdditionalDiscountType() != null && order.getAdditionalDiscountValue() != null
                && order.getAdditionalDiscountType().name().equals("PERCENT")) {
            addlLabel.append(" (").append(order.getAdditionalDiscountValue()).append("%)");
        }
        appendEmailFinancialPaddedTableRow(html,
                receiptUtil.escapeHtml(addlLabel.toString()),
                "-" + currencySymbol + CurrencyFormatter.formatAmount(order.getAdditionalDiscountAmount(), currencySymbol));
    }

    private void appendEmailFinancialTotal(StringBuilder html, Order order, String currencySymbol, Locale locale) {
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        html.append("                                            <div style=\"border-top: 2px solid #e5e7eb; margin-top: 8px; padding-top: 12px;\">\n");
        html.append("                                                <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n");
        html.append("                                                    <tr>\n");
        html.append("                                                        <td style=\"font-size: 18px; line-height: 24px; color: #111827; font-weight: 700;\">\n");
        html.append("                                                            ").append(messageUtil.getMessage("receipt.total", locale)).append("\n");
        html.append("                                                        </td>\n");
        html.append("                                                        <td align=\"right\" style=\"font-size: 18px; line-height: 24px; color: #111827; font-weight: 700;\">\n");
        html.append("                                                            ").append(currencySymbol).append(CurrencyFormatter.formatAmount(total, currencySymbol)).append("\n");
        html.append("                                                        </td>\n");
        html.append("                                                    </tr>\n");
        html.append("                                                </table>\n");
        html.append("                                            </div>\n");
    }

    private void appendEmailFinancialPaddedTableRow(StringBuilder html, String labelHtml, String valueHtml) {
        html.append("                                            <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"padding: 8px 0;\">\n");
        appendEmailFinancialRowInsideOpenTable(html, labelHtml, valueHtml);
        html.append("                                            </table>\n");
    }

    private void appendEmailFinancialRowInsideOpenTable(StringBuilder html, String labelHtml, String valueHtml) {
        html.append("                                                <tr>\n");
        html.append("                                                    <td style=\"font-size: 14px; line-height: 20px; color: #6b7280; font-weight: 700;\">\n");
        html.append("                                                        ").append(labelHtml).append("\n");
        html.append("                                                    </td>\n");
        html.append("                                                    <td align=\"right\" style=\"font-size: 14px; line-height: 20px; color: #111827; font-weight: 700;\">\n");
        html.append("                                                        ").append(valueHtml).append("\n");
        html.append("                                                    </td>\n");
        html.append("                                                </tr>\n");
    }

    private String chainCountryName() {
        var chain = restaurantChainConfigProperties.getChain();
        return chain != null ? chain.getCountryName() : null;
    }
}
