package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.GmoLinkPlusProperties;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusSearchTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoLinkPlusSearchTradeServiceImpl implements GmoLinkPlusSearchTradeService {

    private static final String SEARCH_TRADE_PATH = "SearchTrade.idPass";

    private final GmoLinkPlusProperties gmoLinkPlusProperties;

    @Qualifier("gmoRestTemplate")
    private final RestTemplate gmoRestTemplate;

    @Override
    public Optional<GmoLinkPlusTradeLookup> searchTradeByOrderId(String gmoOrderId) {
        if (!gmoLinkPlusProperties.isConfigured()) {
            log.warn("[GMO LinkPlus SearchTrade] Skipped — Link Plus not configured");
            return Optional.empty();
        }
        if (gmoOrderId == null || gmoOrderId.isBlank()) {
            return Optional.empty();
        }
        String orderId = gmoOrderId.trim();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ShopID", gmoLinkPlusProperties.getShopId().trim());
        form.add("ShopPass", gmoLinkPlusProperties.getShopPass().trim());
        form.add("OrderID", orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String url = resolveSearchTradeUrl();
        try {
            ResponseEntity<String> response = gmoRestTemplate.postForEntity(
                    url, new HttpEntity<>(form, headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("[GMO LinkPlus SearchTrade] Empty response for OrderID={}", orderId);
                return Optional.empty();
            }

            Map<String, String> fields = parseUrlEncodedBody(body);
            String errCode = field(fields, "ErrCode");
            if (errCode != null && !errCode.isBlank()) {
                log.warn("[GMO LinkPlus SearchTrade] GMO error for OrderID={}: ErrCode={} ErrInfo={}",
                        orderId, errCode, field(fields, "ErrInfo"));
                return Optional.empty();
            }

            String accessId = field(fields, "AccessID");
            String accessPass = field(fields, "AccessPass");
            if (accessId == null || accessPass == null) {
                log.warn("[GMO LinkPlus SearchTrade] Missing AccessID/AccessPass for OrderID={}", orderId);
                return Optional.empty();
            }
            if (isMaskedAccessPass(accessPass)) {
                log.warn("[GMO LinkPlus SearchTrade] AccessPass still masked for OrderID={}", orderId);
                return Optional.empty();
            }

            return Optional.of(new GmoLinkPlusTradeLookup(
                    accessId.trim(),
                    accessPass.trim(),
                    field(fields, "Status"),
                    field(fields, "OrderID") != null ? field(fields, "OrderID").trim() : orderId));
        } catch (Exception e) {
            log.error("[GMO LinkPlus SearchTrade] Request failed for OrderID={}", orderId, e);
            return Optional.empty();
        }
    }

    static String resolveSearchTradeUrl(GmoLinkPlusProperties properties) {
        String paymentUrl = properties.getPaymentUrl() != null ? properties.getPaymentUrl().trim() : "";
        if (!paymentUrl.isEmpty()) {
            int paymentSegment = paymentUrl.indexOf("/payment/");
            if (paymentSegment >= 0) {
                return paymentUrl.substring(0, paymentSegment + "/payment/".length()) + SEARCH_TRADE_PATH;
            }
            int lastSlash = paymentUrl.lastIndexOf('/');
            if (lastSlash >= 0) {
                return paymentUrl.substring(0, lastSlash + 1) + SEARCH_TRADE_PATH;
            }
        }
        return "https://pt01.mul-pay.jp/payment/" + SEARCH_TRADE_PATH;
    }

    private String resolveSearchTradeUrl() {
        return resolveSearchTradeUrl(gmoLinkPlusProperties);
    }

    static Map<String, String> parseUrlEncodedBody(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(decode(key), decode(value));
        }
        return out;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String field(Map<String, String> fields, String key) {
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    static boolean isMaskedAccessPass(String accessPass) {
        if (accessPass == null || accessPass.isBlank()) {
            return true;
        }
        String trimmed = accessPass.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '*') {
                return false;
            }
        }
        return true;
    }
}
