package com.gulfnet.shared_library.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class TranslationUtils {

    private TranslationUtils() {}

    /**
     * Picks a translation from the list based on preferred locale, falling back to default language,
     * or the first item sorted by the provided comparator if neither is found.
     *
     * @param <T> the type of translation object
     * @param translations the list of translations to search through
     * @param preferredLocale the preferred locale code to match (case-insensitive)
     * @param defaultLanguage the default language code to use as fallback (case-insensitive)
     * @param getLanguageCode function to extract the language code from a translation object
     * @param deterministicOrder comparator to determine the order when no preferred or default match is found
     * @return Optional containing the selected translation, or empty if translations list is null or empty
     */
    public static <T> Optional<T> pickPreferredOrFallback(
            List<T> translations,
            String preferredLocale,
            String defaultLanguage,
            Function<T, String> getLanguageCode,
            Comparator<T> deterministicOrder) {
        if (translations == null || translations.isEmpty()) {
            return Optional.empty();
        }

        if (preferredLocale != null && !preferredLocale.isEmpty()) {
            Optional<T> preferred = translations.stream()
                    .filter(t -> {
                        String code = getLanguageCode.apply(t);
                        return code != null && code.equalsIgnoreCase(preferredLocale);
                    })
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }

        if (defaultLanguage != null && !defaultLanguage.isEmpty()) {
            Optional<T> def = translations.stream()
                    .filter(t -> {
                        String code = getLanguageCode.apply(t);
                        return code != null && code.equalsIgnoreCase(defaultLanguage);
                    })
                    .findFirst();
            if (def.isPresent()) {
                return def;
            }
        }

        return translations.stream()
                .sorted(deterministicOrder)
                .findFirst();
    }

    public static Comparator<String> languageCodeComparator() {
        return (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return a.compareToIgnoreCase(b);
        };
    }

    /**
     * Picks a translation from the list based on preferred locale, falling back to languages
     * from the ordered list in sequence, or returns empty if no match is found.
     *
     * @param <T> the type of translation object
     * @param translations the list of translations to search through
     * @param preferredLocale the preferred locale code to match (case-insensitive)
     * @param orderedLanguages ordered list of language codes to try as fallback (case-insensitive)
     * @param getLanguageCode function to extract the language code from a translation object
     * @return Optional containing the selected translation, or empty if no match is found or translations list is null or empty
     */
    public static <T> Optional<T> pickPreferredOrFromList(
            List<T> translations,
            String preferredLocale,
            List<String> orderedLanguages,
            Function<T, String> getLanguageCode) {
        if (translations == null || translations.isEmpty()) {
            return Optional.empty();
        }

        if (preferredLocale != null && !preferredLocale.isEmpty()) {
            Optional<T> preferred = translations.stream()
                    .filter(t -> {
                        String code = getLanguageCode.apply(t);
                        return code != null && code.equalsIgnoreCase(preferredLocale);
                    })
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }

        if (orderedLanguages != null) {
            for (String lang : orderedLanguages) {
                if (lang == null) continue;
                Optional<T> found = translations.stream()
                        .filter(t -> {
                            String code = getLanguageCode.apply(t);
                            return code != null && code.equalsIgnoreCase(lang);
                        })
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Same language preference order as {@link #pickPreferredOrFromList}, but skips rows whose
     * display text (e.g. name) is null or blank so a missing ja label still resolves to en.
     * If nothing matches that order, returns the first translation with non-blank display text
     * (by language code, case-insensitive) so arbitrary extra locales still surface a name.
     */
    public static <T> Optional<T> pickPreferredOrFromListNonBlank(
            List<T> translations,
            String preferredLocale,
            List<String> orderedLanguages,
            Function<T, String> getLanguageCode,
            Function<T, String> getDisplayText) {
        if (translations == null || translations.isEmpty()) {
            return Optional.empty();
        }

        List<String> languageOrder = new ArrayList<>();
        if (preferredLocale != null && !preferredLocale.isBlank()) {
            languageOrder.add(preferredLocale);
        }
        if (orderedLanguages != null) {
            for (String lang : orderedLanguages) {
                if (lang == null || lang.isBlank()) {
                    continue;
                }
                boolean duplicate = languageOrder.stream()
                        .anyMatch(existing -> existing.equalsIgnoreCase(lang));
                if (!duplicate) {
                    languageOrder.add(lang);
                }
            }
        }

        for (String lang : languageOrder) {
            Optional<T> found = translations.stream()
                    .filter(t -> {
                        String code = getLanguageCode.apply(t);
                        if (code == null || !code.equalsIgnoreCase(lang)) {
                            return false;
                        }
                        String text = getDisplayText.apply(t);
                        return text != null && !text.isBlank();
                    })
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }

        return translations.stream()
                .filter(t -> {
                    String text = getDisplayText.apply(t);
                    return text != null && !text.isBlank();
                })
                .min(Comparator.comparing(
                        t -> Optional.ofNullable(getLanguageCode.apply(t)).orElse(""),
                        String.CASE_INSENSITIVE_ORDER));
    }
}
