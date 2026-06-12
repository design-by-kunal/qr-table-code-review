package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.entity.RestaurantSection;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.exception.QrCodePdfGenerationException;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.shared_library.util.AddressDto;
import com.gulfnet.shared_library.util.AddressFormatter;
import com.gulfnet.shared_library.util.DateTimeUtil;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.util.ReceiptUtil;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/**
 * Service for generating QR code PDFs optimized for thermal printers.
 * 
 * Supports standard thermal printer sizes:
 * - 72mm width (most common)
 * - 80mm width (also common)
 * 
 * Page size is configurable via chain configuration.
 * QR code PDFs are optimized for narrow thermal printer paper with responsive design.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintQrCodeService {

    private static final String HTML_DIV_OPEN = "<div>";
    private static final String HTML_DIV_CLOSE = "</div>";
    private static final String HTML_DIVIDER = "<div class='divider'></div>";

    private final AWSService awsService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final MessageUtil messageUtil;
    private final ReceiptUtil receiptUtil;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantTranslationRepository restaurantTranslationRepository;
    
    /**
     * Fallback default language code for QR label rendering.
     * <p>
     * We use this instead of relying solely on {@code restaurantChainConfigProperties.getChain()},
     * because that object can be null at runtime depending on bootstrapping/profile setup.
     */
    @Value("${restaurant.chain.defaultLanguageCode:en}")
    private String restaurantDefaultLanguageCode;

    /**
     * Generates a QR code PDF optimized for thermal printers (72mm or 80mm width) and uploads to S3
     * 
     * @param restaurant The restaurant entity
     * @param table The table entity
     * @return S3 URL of the generated PDF
     */
    public String generateQrCodePdf(Restaurant restaurant, RestaurantTable table) {
        try {
            RestaurantTable resolvedTable = resolveTableWithRelationships(table);
            Restaurant resolvedRestaurant = restaurant;
            // Always use chain default language for the QR code PDF
            String defaultLangCode = null;
            if (restaurantChainConfigProperties.getChain() != null) {
                defaultLangCode = restaurantChainConfigProperties.getChain().getDefaultLanguageCode();
            }
            if (defaultLangCode == null || defaultLangCode.isBlank()) {
                defaultLangCode = restaurantDefaultLanguageCode;
            }
            String resolvedLang = DateTimeUtil.resolveReceiptDisplayLanguage(defaultLangCode, null);
            Locale targetLocale = Locale.forLanguageTag(resolvedLang);
            String htmlContent = generateQrCodeHtml(resolvedRestaurant, resolvedTable, targetLocale);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Get receipt page size from chain configuration (same as receipt)
            int receiptWidthMm = 72; // Default to 72mm
            int receiptMaxHeightMm = 0; // 0 means unlimited/continuous paper
            
            RestaurantChainConfigProperties.ReceiptPageSize pageSize = restaurantChainConfigProperties.getChain() != null
                    ? restaurantChainConfigProperties.getChain().getReceiptPageSize()
                    : null;
            
            if (pageSize != null && pageSize.getWidthMm() > 0) {
                receiptWidthMm = pageSize.getWidthMm();
                receiptMaxHeightMm = pageSize.getMaxHeightMm() > 0 ? pageSize.getMaxHeightMm() : 0;
            }

            // Convert mm to points (1mm = 2.83465 points)
            float widthPoints = receiptWidthMm * 2.83465f;
            // For continuous paper (height = 0), use a reasonable default height
            float heightPoints = receiptMaxHeightMm > 0 ? receiptMaxHeightMm * 2.83465f : 600f;
            
            // Create custom page size for thermal receipt
            Rectangle pageSizeRect = new Rectangle(widthPoints, heightPoints);

            // Configure converter properties for Unicode support
            com.itextpdf.html2pdf.ConverterProperties properties = new com.itextpdf.html2pdf.ConverterProperties();
            properties.setCharset("UTF-8");
            
            // Mirror receipt font setup to ensure JA/TH glyphs render in QR PDFs.
            com.itextpdf.html2pdf.resolver.font.DefaultFontProvider fontProvider = 
                    new com.itextpdf.html2pdf.resolver.font.DefaultFontProvider(false, false, false);
            fontProvider.addStandardPdfFonts();
            fontProvider.addSystemFonts();
            registerBundledJapaneseFontBestEffort(fontProvider);
            properties.setFontProvider(fontProvider);

            // Create PdfDocument with custom page size and convert HTML to PDF
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
            com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            PageSize customPageSize = new PageSize(pageSizeRect);
            pdfDoc.setDefaultPageSize(customPageSize);
            
            HtmlConverter.convertToPdf(htmlContent, pdfDoc, properties);
            pdfDoc.close();
            byte[] pdfBytes = baos.toByteArray();
            InputStream inputStream = new ByteArrayInputStream(pdfBytes);
            String fileName = "qr-code_" + resolvedTable.getTableCode() + "_" + resolvedTable.getId().toString() + ".pdf";
            String s3Key = "qr-codes/" + resolvedRestaurant.getId().toString() + "/pdf/" + fileName;
            
            // Upload PDF with Content-Type header for proper browser preview
            String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, (long) pdfBytes.length, "application/pdf");
            log.info("QR code PDF (locale={}, width={}mm) uploaded to S3 - URL: {}", 
                    targetLocale.getLanguage(), receiptWidthMm, uploadedFileUrl);

            return uploadedFileUrl;
            
        } catch (Exception e) {
            log.error("Error generating QR code PDF", e);
            throw new QrCodePdfGenerationException("Failed to generate QR code PDF", e);
        }
    }

    private RestaurantTable resolveTableWithRelationships(RestaurantTable table) {
        if (table == null || table.getId() == null) {
            return table;
        }
        try {
            RestaurantTable resolvedTable = restaurantTableRepository.findByIdWithRelationships(table.getId()).orElse(table);

            // Preserve in-memory QR URL when caller generated a fresh QR but has not persisted yet.
            if ((resolvedTable.getQrCodeUrl() == null || resolvedTable.getQrCodeUrl().isBlank())
                    && table.getQrCodeUrl() != null
                    && !table.getQrCodeUrl().isBlank()) {
                resolvedTable.setQrCodeUrl(table.getQrCodeUrl());
            }
            return resolvedTable;
        } catch (Exception ex) {
            log.warn("Failed to load table relationships for QR PDF table {}: {}", table.getId(), ex.getMessage());
            return table;
        }
    }

    private List<RestaurantTranslation> getRestaurantTranslationsForDisplay(Restaurant restaurant) {
        if (restaurant == null || restaurant.getId() == null) {
            return List.of();
        }
        try {
            List<RestaurantTranslation> existing = restaurant.getTranslations();
            if (existing != null && !existing.isEmpty()) {
                return existing;
            }
        } catch (Exception ignored) {
            // Keep going and try DB fallback.
        }
        try {
            List<RestaurantTranslation> fromDb =
                    restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());
            return fromDb != null ? fromDb : List.of();
        } catch (Exception ex) {
            log.warn("Failed to load restaurant translations for QR PDF restaurant {}: {}", restaurant.getId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Resolve display name for QR PDFs. Prefer a translation name (prefix-match language codes like en-US),
     * then English-ish, then any available translation name. Avoid falling back to restaurantCode.
     */
    private String resolveRestaurantDisplayNameForQr(Restaurant restaurant, Locale locale) {
        if (restaurant == null) {
            return "Restaurant";
        }
        List<RestaurantTranslation> translations = getRestaurantTranslationsForDisplay(restaurant);
        if (translations == null || translations.isEmpty()) {
            return "Restaurant";
        }

        String lang = (locale != null && locale.getLanguage() != null && !locale.getLanguage().isBlank())
                ? locale.getLanguage()
                : "en";
        String langLower = lang.toLowerCase();

        String name = translations.stream()
                .filter(t -> t != null && t.getLanguageCode() != null && t.getName() != null && !t.getName().isBlank())
                .filter(t -> t.getLanguageCode().equalsIgnoreCase(lang) || t.getLanguageCode().toLowerCase().startsWith(langLower))
                .findFirst()
                .map(RestaurantTranslation::getName)
                .orElse(null);
        if (name != null && !name.isBlank()) {
            return name;
        }

        name = translations.stream()
                .filter(t -> t != null && t.getLanguageCode() != null && t.getName() != null && !t.getName().isBlank())
                .filter(t -> t.getLanguageCode().equalsIgnoreCase("en") || t.getLanguageCode().toLowerCase().startsWith("en"))
                .findFirst()
                .map(RestaurantTranslation::getName)
                .orElse(null);
        if (name != null && !name.isBlank()) {
            return name;
        }

        name = translations.stream()
                .filter(t -> t != null && t.getName() != null && !t.getName().isBlank())
                .findFirst()
                .map(RestaurantTranslation::getName)
                .orElse(null);
        return (name != null && !name.isBlank()) ? name : "Restaurant";
    }

    /**
     * Generates HTML content for QR code PDF optimized for thermal printers.
     * Optimized for narrow width (72mm/80mm) with responsive design.
     */
    private String generateQrCodeHtml(Restaurant restaurant, RestaurantTable table, Locale locale) {
        StringBuilder html = new StringBuilder();
        Locale userLocale = locale != null ? locale : Locale.ENGLISH;
        
        // Extract chain object once for readability and efficiency
        var chain = restaurantChainConfigProperties.getChain();
        
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
            html.append("<!DOCTYPE html>");
            html.append("<html><head>");
            html.append("<meta charset='UTF-8'>");
            html.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
            html.append("<meta name='viewport' content='width=").append(receiptWidthMm).append("mm, initial-scale=1.0'>");
            html.append("<style>");
            
            // Base styles optimized for thermal printer (narrow width)
            html.append("@page { size: ").append(receiptWidthMm).append("mm auto; margin: 2mm; }");
            html.append("body { ");
            html.append("font-family: 'NotoSansCJKjp-Regular', 'Noto Sans JP', Arial, 'Helvetica Neue', Helvetica, sans-serif; ");
            html.append("font-size: 11px; ");
            html.append("margin: 0; ");
            html.append("padding: 2mm; ");
            html.append("width: ").append(receiptWidthMm - 4).append("mm; ");
            html.append("max-width: 100%; ");
            html.append("line-height: 1.3; ");
            html.append("}");
            
            // Header styles - elegant fine dining design
            html.append(".logo { max-width: 100%; max-height: 40px; margin: 0 auto 8px; display: block; }");
            html.append(".header { text-align: center; margin-bottom: 12px; padding-bottom: 8px; }");
            html.append(".restaurant-name { font-size: 13px; font-weight: bold; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }");
            html.append(".address { font-size: 9px; margin-bottom: 3px; white-space: pre-line; word-break: break-word; }");
            html.append(".qr-title { font-size: 12px; font-weight: bold; margin: 8px 0 4px 0; text-transform: uppercase; letter-spacing: 1px; }");
            
            // Table info styles
            html.append(".table-info { margin-bottom: 10px; font-size: 10px; text-align: center; }");
            html.append(".table-info div { margin: 3px 0; }");
            html.append(".table-info strong { font-weight: bold; }");
            html.append(".table-code { font-size: 14px; font-weight: bold; margin: 8px 0; }");
            
            // QR code image styles
            html.append(".qr-code-container { text-align: center; margin: 12px 0; }");
            html.append(".qr-code-image { max-width: 100%; height: auto; max-height: 200px; }");
            
            // Footer styles
            html.append(".footer { text-align: center; margin-top: 12px; padding-top: 8px; }");
            html.append(".instruction { font-size: 9px; color: #666; margin-top: 8px; }");
            
            // Divider style
            html.append(".divider { border-top: 1px solid #000; margin: 8px 0; }");
            
            // Responsive adjustments
            html.append("@media print { ");
            html.append("body { width: ").append(receiptWidthMm - 4).append("mm; } ");
            html.append("}");
            
            html.append("</style>");
            html.append("</head><body>");
            
            // Header
            html.append("<div class='header'>");
            if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isBlank()) {
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
            html.append("<div class='restaurant-name'>")
                    .append(receiptUtil.escapeHtml(resolveRestaurantDisplayNameForQr(restaurant, userLocale)))
                    .append(HTML_DIV_CLOSE);
            AddressDto addressDto = AddressDto.fromRestaurant(restaurant, chainCountryName());
            String formattedAddress = AddressFormatter.format(addressDto, userLocale);
            if (!formattedAddress.isEmpty()) {
                html.append("<div class='address'>").append(receiptUtil.escapeHtml(formattedAddress)).append(HTML_DIV_CLOSE);
            }
            if (restaurant.getGstNumber() != null && !restaurant.getGstNumber().isBlank()) {
                String gstLabel = messageUtil.getMessage("receipt.gst.number", userLocale);
                html.append("<div class='address'><strong>").append(gstLabel).append(":</strong> ").append(receiptUtil.escapeHtml(restaurant.getGstNumber())).append(HTML_DIV_CLOSE);
            }
            String qrTitle = messageUtil.getMessage("qr.code.title", userLocale, "QR CODE");
            html.append("<div class='qr-title'>").append(qrTitle).append(HTML_DIV_CLOSE);
            html.append(HTML_DIV_CLOSE);
            
            // Divider
            html.append(HTML_DIVIDER);
            
            // Table Information
            html.append("<div class='table-info'>");
            
            // Check if table is virtual - show only "Take away" for virtual tables
            if (Boolean.TRUE.equals(table.getIsVirtual())) {
                String takeAwayLabel = messageUtil.getMessage("virtual.table.code.takeaway", userLocale, "Take away");
                html.append("<div class='table-code'>").append(takeAwayLabel).append(HTML_DIV_CLOSE);
            } else {
                // Show detailed table information for non-virtual tables
                String tableLabel = messageUtil.getMessage("table", userLocale, "Table");
                html.append(HTML_DIV_OPEN).append(tableLabel).append(": ")
                        .append(receiptUtil.escapeHtml(table.getTableCode())).append(HTML_DIV_CLOSE);

                // Get section name (localized)
                try {
                    if (table.getRestaurantRow() != null && table.getRestaurantRow().getRestaurantSection() != null) {
                        RestaurantSection section = table.getRestaurantRow().getRestaurantSection();
                        String sectionName = receiptUtil.getLocalizedName(section.getTranslations(), userLocale, "Default Section");
                        String sectionLabel = messageUtil.getMessage("section", userLocale, "Section");
                        html.append(HTML_DIV_OPEN).append(sectionLabel).append(": ").append(receiptUtil.escapeHtml(sectionName)).append(HTML_DIV_CLOSE);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get section name for table {}: {}", table.getId(), e.getMessage());
                }
                
                // Get row order
                try {
                    if (table.getRestaurantRow() != null && table.getRestaurantRow().getRowOrder() != null) {
                        String rowLabel = messageUtil.getMessage("row", userLocale, "Row");
                        html.append(HTML_DIV_OPEN).append(rowLabel).append(": ").append(table.getRestaurantRow().getRowOrder()).append(HTML_DIV_CLOSE);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get row order for table {}: {}", table.getId(), e.getMessage());
                }
                
                // Get capacity
                if (table.getCapacity() != null) {
                    String capacityLabel = messageUtil.getMessage("capacity", userLocale, "Capacity");
                    String seatsLabel = messageUtil.getMessage("seats", userLocale, "seats");
                    html.append(HTML_DIV_OPEN).append(capacityLabel).append(": ").append(table.getCapacity()).append(" ").append(seatsLabel).append(HTML_DIV_CLOSE);
                }
            }
            
            html.append(HTML_DIV_CLOSE);
            
            // Divider
            html.append(HTML_DIVIDER);
            
            // QR Code Image
            html.append("<div class='qr-code-container'>");
            if (table.getQrCodeUrl() != null && !table.getQrCodeUrl().isBlank()) {
                try {
                    String qrCodeKeyOrUrl = table.getQrCodeUrl();
                    String signedQrCodeUrl = awsService.getPreSignedUrl(qrCodeKeyOrUrl);
                    if (signedQrCodeUrl != null && !signedQrCodeUrl.isEmpty() && !signedQrCodeUrl.equals("location")) {
                        String safeUrl = signedQrCodeUrl.replace("'", "&#39;").replace("\"", "&quot;");
                        html.append("<img class='qr-code-image' src=\"").append(safeUrl).append("\" alt=\"QR Code\"/>");
                    }
                } catch (Exception e) {
                    log.error("Error generating presigned URL for QR code: {}", table.getQrCodeUrl(), e);
                }
            }
            html.append(HTML_DIV_CLOSE);
            
            // Divider before footer
            html.append(HTML_DIVIDER);
            
            // Footer
            html.append("<div class='footer'>");
            String instructionText = messageUtil.getMessage("qr.code.instruction", userLocale, "Scan this QR code to view the menu and place your order");
            html.append("<div class='instruction'>").append(instructionText).append(HTML_DIV_CLOSE);
            html.append(HTML_DIV_CLOSE);
            
            html.append("</body></html>");
            
            return html.toString();
            
        } finally {
            // Restore original locale
            LocaleContextHolder.setLocale(originalLocale);
        }
    }

    private String chainCountryName() {
        var chain = restaurantChainConfigProperties.getChain();
        return chain != null ? chain.getCountryName() : null;
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
            log.warn("Bundled Japanese font not found or failed to register for QR PDF; JA headers may not render correctly: {}", ex.getMessage());
        }
    }
}
