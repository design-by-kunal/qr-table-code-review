package com.gulfnet.shared_library.util;

import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.chrono.ThaiBuddhistDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Thread-safe localized {@link LocalDateTime} formatting for receipts (en / ja / th).
 */
public final class DateTimeUtil {

    /** English: April 3, 2026 14:30 */
    public static final String PATTERN_EN = "MMMM d, yyyy HH:mm";

    /** Japanese: 2026年4月3日 14:30 */
    public static final String PATTERN_JA = "yyyy年M月d日 HH:mm";

    /** Thai (Buddhist era year): 3 เมษายน 2569 14:30 */
    public static final String PATTERN_TH = "d MMMM yyyy HH:mm";

    private static final DateTimeFormatter EN_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_EN, Locale.ENGLISH);

    private static final DateTimeFormatter JA_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_JA, Locale.JAPAN);

    private static final DateTimeFormatter TH_FORMATTER =
            DateTimeFormatter.ofPattern(PATTERN_TH, Locale.forLanguageTag("th-TH"))
                    .withChronology(ThaiBuddhistChronology.INSTANCE);

    private DateTimeUtil() {
    }

    /**
     * Resolves receipt language: explicit override, then {@link LocaleContextHolder}, then chain default.
     *
     * @param languageOverride          optional request or caller language; may be null
     * @param chainDefaultLanguageCode optional chain-config default; may be null
     */
    public static String resolveReceiptLanguage(String languageOverride, String chainDefaultLanguageCode) {
        if (languageOverride != null && !languageOverride.isBlank()) {
            return normalizeReceiptLanguage(languageOverride);
        }
        Locale ctx = LocaleContextHolder.getLocale();
        if (ctx != null) {
            String fromCtx = ctx.getLanguage();
            if (fromCtx != null && !fromCtx.isBlank()) {
                return normalizeReceiptLanguage(fromCtx);
            }
        }
        if (chainDefaultLanguageCode != null && !chainDefaultLanguageCode.isBlank()) {
            return normalizeReceiptLanguage(chainDefaultLanguageCode);
        }
        return "en";
    }

    /**
     * Language for receipt PDFs and receipt emails: when chain {@code defaultLanguageCode} is configured,
     * that value always wins (normalized), ignoring request locale and {@code languageOverride}.
     * When chain default is absent, falls back to {@link #resolveReceiptLanguage(String, String)} with
     * {@code languageOverride} and no chain (request {@link LocaleContextHolder}, then {@code en}).
     *
     * @param chainDefaultLanguageCode from chain configuration; may be null or blank
     * @param languageOverride         used only when chain default is blank; may be null
     */
    public static String resolveReceiptDisplayLanguage(String chainDefaultLanguageCode, String languageOverride) {
        if (chainDefaultLanguageCode != null && !chainDefaultLanguageCode.isBlank()) {
            return normalizeReceiptLanguage(chainDefaultLanguageCode);
        }
        return resolveReceiptLanguage(languageOverride, null);
    }

    /**
     * Normalizes a language tag to a supported receipt language code: {@code en}, {@code ja}, or {@code th}.
     * Unknown or blank values fall back to {@code en}.
     */
    public static String normalizeReceiptLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String primary = language.toLowerCase(Locale.ROOT).trim();
        int us = primary.indexOf('_');
        int dash = primary.indexOf('-');
        int cut = -1;
        if (us >= 0 && dash >= 0) {
            cut = Math.min(us, dash);
        } else {
            cut = Math.max(us, dash);
        }
        if (cut > 0) {
            primary = primary.substring(0, cut);
        }
        return switch (primary) {
            case "ja" -> "ja";
            case "th" -> "th";
            default -> "en";
        };
    }

    /**
     * Formats a date-time for the given language.
     *
     * @param dateTime the instant to format; {@code null} yields an empty string
     * @param language ISO language tag or code (e.g. {@code en}, {@code ja}, {@code th-TH}); normalized via {@link #normalizeReceiptLanguage(String)}
     * @return localized string, or empty string if {@code dateTime} is null
     */
    public static String format(LocalDateTime dateTime, String language) {
        if (dateTime == null) {
            return "";
        }
        String lang = normalizeReceiptLanguage(language);
        return switch (lang) {
            case "ja" -> dateTime.format(JA_FORMATTER);
            case "th" -> formatThaiBuddhist(dateTime);
            default -> dateTime.format(EN_FORMATTER);
        };
    }

    private static String formatThaiBuddhist(LocalDateTime dateTime) {
        ThaiBuddhistDate thaiDate = ThaiBuddhistDate.from(dateTime.toLocalDate());
        ChronoLocalDateTime<ThaiBuddhistDate> thaiDateTime = thaiDate.atTime(dateTime.toLocalTime());
        return TH_FORMATTER.format(thaiDateTime);
    }
}
