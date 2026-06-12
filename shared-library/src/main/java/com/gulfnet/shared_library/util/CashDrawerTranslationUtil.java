package com.gulfnet.shared_library.util;

import com.gulfnet.shared_library.entity.CashDrawerTranslation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CashDrawerTranslationUtil {

    private CashDrawerTranslationUtil() {
    }

    /**
     * Picks the cash-drawer display name from {@code translations} using the request locale's
     * {@link Locale#getLanguage() language code} (case-insensitive match on {@link CashDrawerTranslation#getLanguageCode}).
     * If none match, falls back to English ({@code en}), then to the first entry in the list.
     *
     * @param translations translation rows for one drawer; {@code null} or empty yields {@code ""}
     * @param locale         preferred language; {@code null} is treated like {@code en}
     * @return {@link CashDrawerTranslation#getName()} from the chosen row, {@code ""} when there are no translations,
     *         or {@code null} if that row has no stored name
     */
    public static String resolveName(List<CashDrawerTranslation> translations, Locale locale) {
        if (translations == null || translations.isEmpty()) {
            return "";
        }
        String preferred = locale != null ? locale.getLanguage() : "en";
        String defaultLanguage = "en";

        Optional<CashDrawerTranslation> byPreferred = translations.stream()
                .filter(t -> t.getLanguageCode() != null && preferred.equalsIgnoreCase(t.getLanguageCode()))
                .findFirst();
        if (byPreferred.isPresent()) {
            return byPreferred.get().getName();
        }

        Optional<CashDrawerTranslation> byDefault = translations.stream()
                .filter(t -> defaultLanguage.equalsIgnoreCase(t.getLanguageCode()))
                .findFirst();
        if (byDefault.isPresent()) {
            return byDefault.get().getName();
        }

        return translations.get(0).getName();
    }

    public static String resolveName(List<CashDrawerTranslation> translations, String preferredLanguageCode) {
        if (preferredLanguageCode == null || preferredLanguageCode.isBlank()) {
            return resolveName(translations, Locale.ENGLISH);
        }
        return resolveName(translations, Locale.forLanguageTag(preferredLanguageCode));
    }
}
