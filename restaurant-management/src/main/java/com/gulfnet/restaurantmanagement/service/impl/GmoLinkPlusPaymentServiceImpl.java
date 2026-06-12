package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.restaurantmanagement.config.GmoLinkPlusProperties;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoLinkPlusPaymentServiceImpl implements GmoLinkPlusPaymentService {

    /** GMO interprets {@code PaymentExpireDate} in Japan time ({@code Asia/Tokyo}). */
    private static final ZoneId GMO_PAYMENT_EXPIRE_ZONE = ZoneId.of("Asia/Tokyo");

    /** GMO {@code transaction.PaymentExpireDate}: {@code yyyyMMddHHmm} (12 digits). */
    private static final DateTimeFormatter GMO_PAYMENT_EXPIRE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final GmoLinkPlusProperties gmoLinkPlusProperties;
    private final ObjectMapper objectMapper;

    /**
     * Dedicated RestTemplate for GMO Link Plus calls.
     * Defined separately from the Omise client to avoid accidental coupling.
     */
    @Qualifier("gmoRestTemplate")
    private final RestTemplate gmoRestTemplate;

    @Override
    public boolean isConfigured() {
        return gmoLinkPlusProperties.isConfigured();
    }

    @Override
    public String createHostedCheckoutUrl(String gmoOrderId,
                                          BigDecimal amount,
                                          BigDecimal tax,
                                          String retUrl,
                                          String completeUrl,
                                          String cancelUrl,
                                          String resultSkipFlag,
                                          Locale displayLocale) {
        if (!gmoLinkPlusProperties.isConfigured()) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    "GMO LinkType Plus is not configured (set GMO_LINK_PLUS_PAYMENT_URL, GMO_LINK_PLUS_SHOP_ID, GMO_LINK_PLUS_SHOP_PASS, GMO_LINK_PLUS_CONFIG_ID).");
        }

        long amountInt = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        long taxInt = tax == null ? 0 : tax.setScale(0, RoundingMode.HALF_UP).longValueExact();

        Map<String, Object> body = buildRequestBody(
                gmoOrderId, amountInt, taxInt, retUrl, completeUrl, cancelUrl, resultSkipFlag, displayLocale);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/json; charset=UTF-8"));
        headers.setAccept(List.of(MediaType.parseMediaType("application/json; charset=UTF-8")));

        String url = gmoLinkPlusProperties.getPaymentUrl().trim();
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = gmoRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return parseSuccessBody(gmoOrderId, response.getBody());
        } catch (GmoLinkPlusTradeStatusSupport.OrderIdInUseException
                | GmoLinkPlusTradeStatusSupport.DoubleSubmissionException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            String raw = e.getResponseBodyAsString();
            if (GmoLinkPlusTradeStatusSupport.isDoubleSubmissionResponse(objectMapper, raw)) {
                log.info("GMO LinkType Plus double submission (E90010001): orderId={}", gmoOrderId);
                throw new GmoLinkPlusTradeStatusSupport.DoubleSubmissionException(gmoOrderId);
            }
            if (GmoLinkPlusTradeStatusSupport.isOrderIdInUseResponse(objectMapper, raw)) {
                log.info("GMO LinkType Plus OrderID in use (EZ4135014): orderId={}", gmoOrderId);
                throw new GmoLinkPlusTradeStatusSupport.OrderIdInUseException(gmoOrderId);
            }
            log.warn("GMO LinkType Plus HTTP {}: orderId={}, body={}", e.getStatusCode().value(), gmoOrderId, raw);
            throw new ResponseStatusException(BAD_GATEWAY, formatGmoFailureMessage(raw));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMO LinkType Plus GetLinkplusUrlPayment failed for orderId={}", gmoOrderId, e);
            throw new ResponseStatusException(BAD_GATEWAY, "GMO LinkType Plus request failed: " + e.getMessage());
        }
    }

    private String parseSuccessBody(String gmoOrderId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Empty response from GMO LinkType Plus");
        }
        if (GmoLinkPlusTradeStatusSupport.isDoubleSubmissionResponse(objectMapper, raw)) {
            log.info("GMO LinkType Plus double submission (E90010001) in body: orderId={}", gmoOrderId);
            throw new GmoLinkPlusTradeStatusSupport.DoubleSubmissionException(gmoOrderId);
        }
        if (GmoLinkPlusTradeStatusSupport.isOrderIdInUseResponse(objectMapper, raw)) {
            log.info("GMO LinkType Plus OrderID in use (EZ4135014) in success-shaped body: orderId={}", gmoOrderId);
            throw new GmoLinkPlusTradeStatusSupport.OrderIdInUseException(gmoOrderId);
        }
        String linkUrl = GmoLinkPlusTradeStatusSupport.extractLinkUrl(objectMapper, raw);
        if (linkUrl != null) {
            return linkUrl;
        }
        log.warn("GMO LinkType Plus response missing LinkUrl for orderId={}, body={}", gmoOrderId, raw);
        throw new ResponseStatusException(BAD_GATEWAY, formatGmoFailureMessage(raw));
    }

    private String formatGmoFailureMessage(String raw) {
        if (GmoLinkPlusTradeStatusSupport.isDoubleSubmissionResponse(objectMapper, raw)) {
            throw new GmoLinkPlusTradeStatusSupport.DoubleSubmissionException("");
        }
        String detail = GmoLinkPlusTradeStatusSupport.extractGmoErrorDetail(objectMapper, raw);
        if (detail.isBlank()) {
            return "GMO LinkType Plus request failed";
        }
        return "GMO LinkType Plus request failed: " + detail;
    }

    /**
     * GMO LinkType Plus {@code displaysetting.Lang}: ISO639-style codes documented as
     * {@code ja}, {@code en}, {@code zh} (simplified Chinese). Unsupported languages (e.g. {@code th})
     * fall back to {@code ja} so the hosted page matches the typical Japan-region default.
     */
    static String toGmoCheckoutLang(Locale locale) {
        if (locale == null) {
            return "ja";
        }
        String lang = locale.getLanguage();
        if (lang == null || lang.isBlank()) {
            return "ja";
        }
        return switch (lang.toLowerCase(Locale.ROOT)) {
            case "ja" -> "ja";
            case "zh" -> "zh";
            case "en" -> "en";
            default -> "ja";
        };
    }

    private Map<String, Object> buildRequestBody(String gmoOrderId,
                                                 long amountInt,
                                                 long taxInt,
                                                 String retUrl,
                                                 String completeUrl,
                                                 String cancelUrl,
                                                 String resultSkipFlag,
                                                 Locale displayLocale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configid", gmoLinkPlusProperties.getConfigId());

        Map<String, String> geturlparam = new LinkedHashMap<>();
        geturlparam.put("ShopID", gmoLinkPlusProperties.getShopId().trim());
        geturlparam.put("ShopPass", gmoLinkPlusProperties.getShopPass().trim());
        body.put("geturlparam", geturlparam);

        String gmoLang = toGmoCheckoutLang(displayLocale);
        Map<String, String> displaysetting = new LinkedHashMap<>();
        displaysetting.put("Lang", gmoLang);
        body.put("displaysetting", displaysetting);
        log.info("GMO LinkType Plus displaysetting.Lang={} (from locale={})", gmoLang, displayLocale);

        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("OrderID", gmoOrderId);
        transaction.put("Amount", amountInt);
        transaction.put("Tax", taxInt);
        transaction.put("RetUrl", retUrl);
        transaction.put("CompleteUrl", completeUrl);
        transaction.put("CancelUrl", cancelUrl);
        transaction.put("ResultSkipFlag", (resultSkipFlag != null && !resultSkipFlag.isBlank()) ? resultSkipFlag.trim() : "1");
        transaction.put("PayMethods", List.of("credit"));

        int expiresMinutes = gmoLinkPlusProperties.getPaymentExpiresMinutes();
        if (expiresMinutes > 0) {
            OffsetDateTime expiresAt = OffsetDateTime.now(GMO_PAYMENT_EXPIRE_ZONE).plusMinutes(expiresMinutes);
            String paymentExpireDate = expiresAt.format(GMO_PAYMENT_EXPIRE_DATE);
            transaction.put("PaymentExpireDate", paymentExpireDate);
            log.info("GMO LinkType Plus PaymentExpireDate={} ({} minutes from now, Asia/Tokyo)",
                    paymentExpireDate, expiresMinutes);
        }

        body.put("transaction", transaction);

        Map<String, String> credit = new LinkedHashMap<>();
        credit.put("JobCd", "CAPTURE");
        credit.put("Method", "1");
        body.put("credit", credit);
        return body;
    }
}
