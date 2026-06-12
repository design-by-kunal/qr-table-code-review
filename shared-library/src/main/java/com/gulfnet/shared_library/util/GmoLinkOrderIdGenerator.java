package com.gulfnet.shared_library.util;

import java.util.Locale;
import java.util.UUID;

/**
 * Generates {@code OrderID} values for GMO PG LinkType Plus (credit card hosted checkout).
 * <p>
 * Per GMO docs: required, unique per transaction, max 27 chars, half-width alphanumeric/symbols.
 * We use uppercase hex only (alphanumeric) and fixed length 27 for predictable validation.
 */
public final class GmoLinkOrderIdGenerator {

    public static final int GMO_ORDER_ID_MAX_LENGTH = 27;

    private GmoLinkOrderIdGenerator() {
    }

    /**
     * @return a new candidate ID (27 chars: {@code G} + 26 hex from random UUID). Not guaranteed unique until checked against DB.
     */
    public static String generateCandidate() {
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return "G" + hex.substring(0, Math.min(26, hex.length()));
    }

    public static boolean isValidFormat(String value) {
        if (value == null || value.isEmpty() || value.length() > GMO_ORDER_ID_MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')) {
                return false;
            }
        }
        return true;
    }
}
