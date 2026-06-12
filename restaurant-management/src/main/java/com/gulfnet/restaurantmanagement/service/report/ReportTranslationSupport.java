package com.gulfnet.restaurantmanagement.service.report;

import com.gulfnet.shared_library.entity.CategoryTranslation;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.entity.ItemTranslation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Small helper for resolving entity display names from translation maps for reports.
 * Keeps {@link OverviewReportQueryService} readable and avoids repeated fallback logic.
 */
@Component
public class ReportTranslationSupport {

    public String getItemName(UUID itemId, Map<UUID, List<ItemTranslation>> itemTranslationsMap, String localeTag) {
        if (itemId == null || itemTranslationsMap == null) {
            return null;
        }
        return resolveName(itemTranslationsMap.get(itemId), localeTag);
    }

    public String getComboName(UUID comboId, Map<UUID, List<ComboTranslation>> comboTranslationsMap, String localeTag) {
        if (comboId == null || comboTranslationsMap == null) {
            return null;
        }
        return resolveName(comboTranslationsMap.get(comboId), localeTag);
    }

    public String getCategoryName(UUID categoryId, Map<UUID, List<CategoryTranslation>> categoryTranslationsMap, String localeTag) {
        if (categoryId == null || categoryTranslationsMap == null) {
            return null;
        }
        return resolveName(categoryTranslationsMap.get(categoryId), localeTag);
    }

    private String resolveName(List<?> translations, String localeTag) {
        if (translations == null || translations.isEmpty()) {
            return "";
        }

        String normalized = normalizeLocale(localeTag);

        // Try exact match first.
        for (Object t : translations) {
            String lang = languageCodeOf(t);
            String name = nameOf(t);
            if (name != null && !name.isBlank() && lang != null && lang.equalsIgnoreCase(normalized)) {
                return name;
            }
        }

        // Then try English.
        for (Object t : translations) {
            String lang = languageCodeOf(t);
            String name = nameOf(t);
            if (name != null && !name.isBlank() && "en".equalsIgnoreCase(lang)) {
                return name;
            }
        }

        // Finally: first non-blank.
        for (Object t : translations) {
            String name = nameOf(t);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }

        return "";
    }

    private String normalizeLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) {
            return "en";
        }
        // Accept "en-US" etc and normalize to language.
        Locale loc = Locale.forLanguageTag(localeTag);
        String lang = loc.getLanguage();
        return (lang == null || lang.isBlank()) ? "en" : lang;
    }

    private String languageCodeOf(Object translation) {
        if (translation instanceof ItemTranslation it) return it.getLanguageCode();
        if (translation instanceof ComboTranslation ct) return ct.getLanguageCode();
        if (translation instanceof CategoryTranslation cat) return cat.getLanguageCode();
        return null;
    }

    private String nameOf(Object translation) {
        if (translation instanceof ItemTranslation it) return it.getName();
        if (translation instanceof ComboTranslation ct) return ct.getName();
        if (translation instanceof CategoryTranslation cat) return cat.getName();
        return null;
    }
}

