package com.gulfnet.restaurantmanagement.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last display locale per user from WebSocket STOMP CONNECT ({@code locale} header or {@code ?locale=} query).
 * Used so KDS and waiter pop-ups can follow the live app language (like REST {@code locale}) before profile language.
 */
@Component
public class WebSocketClientLocaleRegistry {

    private final ConcurrentHashMap<String, Locale> userIdToLocale = new ConcurrentHashMap<>();

    private final LocalizationProperties localizationProperties;

    public WebSocketClientLocaleRegistry(LocalizationProperties localizationProperties) {
        this.localizationProperties = localizationProperties;
    }

    public void recordLocale(String userId, String localeTag) {
        if (userId == null || userId.isBlank() || localeTag == null || localeTag.isBlank()) {
            return;
        }
        Locale parsed = parseIfSupported(localeTag.trim());
        if (parsed != null) {
            userIdToLocale.put(userId.trim(), parsed);
        }
    }

    /**
     * Locale recorded from the user's last WebSocket CONNECT, or null if none.
     */
    public Locale getRecordedLocale(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userIdToLocale.get(userId.trim());
    }

    /**
     * KDS fallback when profile {@code languageCode} is unset: WebSocket locale, then trigger HTTP locale, then English.
     */
    public Locale resolveLocaleForKds(String userId, Locale triggerLocale) {
        Locale wired = getRecordedLocale(userId);
        if (wired != null) {
            return wired;
        }
        if (triggerLocale != null) {
            return triggerLocale;
        }
        return Locale.ENGLISH;
    }

    /**
     * Parses {@code localeTag} when it matches a configured supported language; otherwise returns {@code null}.
     *
     * @param localeTag normalized non-blank BCP 47 tag (already trimmed by caller)
     */
    private Locale parseIfSupported(String localeTag) {
        List<String> supported = localizationProperties.getLanguages();
        String lower = localeTag.toLowerCase(Locale.ROOT);
        if (supported == null || supported.isEmpty()) {
            return Locale.forLanguageTag(lower);
        }
        boolean ok = supported.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(lower));
        if (!ok) {
            return null;
        }
        return Locale.forLanguageTag(lower);
    }
}
