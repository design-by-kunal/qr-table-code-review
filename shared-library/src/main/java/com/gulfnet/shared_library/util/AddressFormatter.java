package com.gulfnet.shared_library.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats structured addresses for receipts and emails by locale (EN / JA / TH).
 */
public final class AddressFormatter {

    private AddressFormatter() {
    }

    /**
     * Returns a multi-line postal address string for the given locale: Japanese ({@code ja}), Thai
     * ({@code th}), or English-style formatting for any other language (including {@code en} and
     * {@code null} locale, which is treated like English).
     *
     * @param address structured address fields; {@code null} yields an empty string
     * @param locale    determines layout; only the {@link Locale#getLanguage() language} tag is used
     * @return lines separated by newline characters; never {@code null}
     */
    public static String format(AddressDto address, Locale locale) {
        if (address == null) {
            return "";
        }
        String lang = locale != null ? locale.getLanguage() : "";
        if ("ja".equalsIgnoreCase(lang)) {
            return formatJapanese(address);
        }
        if ("th".equalsIgnoreCase(lang)) {
            return formatThai(address);
        }
        return formatEnglish(address);
    }

    /**
     * English-style block: {@link AddressDto#address2()}, {@link AddressDto#address1()}, {@link AddressDto#area()},
     * then {@link #joinCityStateEnglish} for city/state on one line, then {@link AddressDto#locationPin()}
     * and {@link AddressDto#country()}. Lines are skipped only for {@code null} fields ({@link #addIfPresent}).
     */
    private static String formatEnglish(AddressDto a) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, a.address2());
        addIfPresent(lines, a.address1());
        addIfPresent(lines, a.area());
        String cityState = joinCityStateEnglish(a.city(), a.state());
        if (cityState != null) {
            lines.add(cityState);
        }
        addIfPresent(lines, a.locationPin());
        addIfPresent(lines, a.country());
        return joinLines(lines);
    }

    /**
     * Builds the {@code "City, State"} line used in {@link #formatEnglish}: both present yields
     * {@code city + ", " + state}; a single non-{@code null} value is returned alone; if both are
     * {@code null}, returns {@code null} so the caller can skip the line.
     *
     * @param city  may be {@code null}; empty string is still treated as present
     * @param state may be {@code null}; empty string is still treated as present
     * @return combined city/state, a single field, or {@code null}
     */
    private static String joinCityStateEnglish(String city, String state) {
        boolean hasCity = city != null;
        boolean hasState = state != null;
        if (hasCity && hasState) {
            return city + ", " + state;
        }
        if (hasCity) {
            return city;
        }
        if (hasState) {
            return state;
        }
        return null;
    }

    private static String formatThai(AddressDto a) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, a.address2());
        addIfPresent(lines, a.address1());
        addIfPresent(lines, a.area());
        addIfPresent(lines, a.city());
        addIfPresent(lines, a.state());
        addIfPresent(lines, a.locationPin());
        addIfPresent(lines, a.country());
        return joinLines(lines);
    }

    /**
     * Japanese: fixed field order (one line each, skip blanks) —
     * 〒locationPin, country, state, city, area, address1, address2.
     */
    private static String formatJapanese(AddressDto a) {
        List<String> lines = new ArrayList<>();
        if (a.locationPin() != null) {
            lines.add("\u3012" + a.locationPin());
        }
        addIfPresent(lines, a.country());
        addIfPresent(lines, a.state());
        addIfPresent(lines, a.city());
        addIfPresent(lines, a.area());
        addIfPresent(lines, a.address1());
        addIfPresent(lines, a.address2());
        return joinLines(lines);
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null) {
            lines.add(value);
        }
    }

    private static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }
}
