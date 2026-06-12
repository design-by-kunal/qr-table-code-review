package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.GmoLinkPlusNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GMO PG <strong>result notification</strong> receiver for LinkType Plus (hosted card).
 * <p>
 * Register the HTTPS URL of {@code POST /api/v1/gmo/link-plus/notify} in the shop admin
 * (Payment result notification settings). For local testing, expose this service with ngrok and paste that URL.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gmo/link-plus")
@RequiredArgsConstructor
public class GmoLinkPlusNotificationController {

    private final GmoLinkPlusNotificationService gmoLinkPlusNotificationService;

    /**
     * GMO sends {@code application/x-www-form-urlencoded} with fields such as {@code OrderID}, {@code Status},
     * {@code AccessID}, {@code AccessPass}, {@code ErrCode}, {@code ShopID}. Response must be plain {@code 0} (ok) or {@code 1} (retry).
     */
    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> notify(@RequestParam MultiValueMap<String, String> form) {
        logIncomingForm(form);
        Map<String, String> flat = flatten(form);
        String body = gmoLinkPlusNotificationService.processResultNotification(flat);
        log.info("[GMO LinkPlus notify] Responding with body={}", body);
        return ResponseEntity.ok(body);
    }

    private static void logIncomingForm(MultiValueMap<String, String> form) {
        if (form == null || form.isEmpty()) {
            log.info("[GMO LinkPlus notify] Incoming POST /notify with no form parameters");
            return;
        }
        log.info("[GMO LinkPlus notify] Incoming POST /notify ({} parameter keys)", form.size());
        form.forEach((key, values) -> {
            if (key == null) {
                return;
            }
            if (values == null || values.isEmpty()) {
                log.info("[GMO LinkPlus notify]   {}=<empty>", key);
            } else if (values.size() == 1) {
                log.info("[GMO LinkPlus notify]   {}={}", key, formatNotifyValueForLog(key, values.get(0)));
            } else {
                log.info("[GMO LinkPlus notify]   {}={}", key, values);
            }
        });
    }

    private static String formatNotifyValueForLog(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        if ("AccessPass".equalsIgnoreCase(key.trim()) || "ShopPass".equalsIgnoreCase(key.trim())) {
            String trimmed = value.trim();
            if (trimmed.length() <= 4) {
                return "****";
            }
            return trimmed.substring(0, 4) + "****(len=" + trimmed.length() + ")";
        }
        return value;
    }

    private static Map<String, String> flatten(MultiValueMap<String, String> form) {
        Map<String, String> out = new LinkedHashMap<>();
        if (form == null) {
            return out;
        }
        form.forEach((key, values) -> {
            if (key == null) {
                return;
            }
            if (values == null || values.isEmpty()) {
                out.put(key, null);
            } else {
                out.put(key, values.get(0));
            }
        });
        return out;
    }
}
