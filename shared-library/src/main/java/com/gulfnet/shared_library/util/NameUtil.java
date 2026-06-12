package com.gulfnet.shared_library.util;

/**
 * Formats person names for display on receipts by locale (Japanese: family name first).
 */
public final class NameUtil {

    private NameUtil() {
    }

    /**
     * Formats first and last name for the given language.
     * <ul>
     *   <li>{@code ja}: last name, space, first name (e.g. 山田 太郎)</li>
     *   <li>{@code en}, {@code th}, and others: first name, space, last name</li>
     * </ul>
     *
     * @param firstName first / given name (nullable)
     * @param lastName  last / family name (nullable)
     * @param language  language code or tag; normalized like {@link DateTimeUtil#normalizeReceiptLanguage(String)}
     * @return trimmed display name without redundant spaces
     */
    public static String formatName(String firstName, String lastName, String language) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String lang = DateTimeUtil.normalizeReceiptLanguage(language);

        if ("ja".equals(lang)) {
            return joinNonBlank(last, first);
        }
        return joinNonBlank(first, last);
    }

    private static String joinNonBlank(String a, String b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }
}
