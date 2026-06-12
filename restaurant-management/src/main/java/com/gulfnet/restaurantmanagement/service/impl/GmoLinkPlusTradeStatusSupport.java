package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared GMO LinkType Plus helpers: trade {@code Status} / notify interpretation and
 * {@code GetLinkplusUrlPayment} response parsing.
 */
final class GmoLinkPlusTradeStatusSupport {

    private static final String ORDER_ID_IN_USE_WARN = "EZ4135014";
    /** GMO concurrent / double-submit while creating a hosted checkout link. */
    private static final String DOUBLE_SUBMISSION_ERR_INFO = "E90010001";

    private GmoLinkPlusTradeStatusSupport() {
    }

    /**
     * Credit capture / sales success for mul-pay result notification or SearchTrade.
     */
    static boolean isPaidTradeStatus(String status, String errCode) {
        if (errCode != null && !errCode.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim();
        return "CAPTURE".equalsIgnoreCase(s)
                || "SALES".equalsIgnoreCase(s)
                || "AUTH".equalsIgnoreCase(s);
    }

    /**
     * Human-readable GMO error from JSON array {@code [{errCode, errInfo}]} or object body.
     */
    static String extractGmoErrorDetail(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                String errCode = first.path("errCode").asText("");
                String errInfo = first.path("errInfo").asText("");
                if (!errCode.isBlank() || !errInfo.isBlank()) {
                    return (errCode + " " + errInfo).trim();
                }
            }
            if (root.isObject()) {
                String errCode = root.path("errCode").asText("");
                String errInfo = root.path("errInfo").asText("");
                if (!errCode.isBlank() || !errInfo.isBlank()) {
                    return (errCode + " " + errInfo).trim();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return raw.length() > 300 ? raw.substring(0, 300) + "..." : raw.trim();
    }

    static String extractLinkUrl(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                String errCode = first.path("errCode").asText("");
                String errInfo = first.path("errInfo").asText("");
                if (!errCode.isBlank() || !errInfo.isBlank()) {
                    return null;
                }
            }
            if (root.isObject()) {
                String linkUrl = root.path("LinkUrl").asText(null);
                if (linkUrl != null && !linkUrl.isBlank()) {
                    return linkUrl;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /**
     * GMO is already processing another link-creation request for this shop/order (concurrent taps).
     */
    static boolean isDoubleSubmissionResponse(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (raw.contains(DOUBLE_SUBMISSION_ERR_INFO)) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (nodeHasDoubleSubmissionError(node)) {
                        return true;
                    }
                }
                return false;
            }
            if (root.isObject()) {
                return nodeHasDoubleSubmissionError(root);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static boolean nodeHasDoubleSubmissionError(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        String errInfo = node.path("errInfo").asText("");
        if (DOUBLE_SUBMISSION_ERR_INFO.equals(errInfo) || errInfo.contains(DOUBLE_SUBMISSION_ERR_INFO)) {
            return true;
        }
        String errCode = node.path("errCode").asText("");
        return "E90".equals(errCode) && errInfo.contains(DOUBLE_SUBMISSION_ERR_INFO);
    }

    static boolean isOrderIdInUseResponse(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isObject()) {
                return false;
            }
            JsonNode warnList = root.path("WarnList");
            if (!warnList.isArray()) {
                return false;
            }
            for (JsonNode warn : warnList) {
                String warnInfo = warn.path("warnInfo").asText("");
                if (ORDER_ID_IN_USE_WARN.equals(warnInfo) || warnInfo.contains(ORDER_ID_IN_USE_WARN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    /**
     * GMO rejected {@code OrderID} because a checkout session is still open (EZ4135014).
     */
    static final class OrderIdInUseException extends RuntimeException {

        OrderIdInUseException(String gmoOrderId) {
            super("GMO LinkType Plus OrderID still in use: " + gmoOrderId);
        }
    }

    /**
     * Another client just started card checkout for the same GMO {@code OrderID} ({@code E90010001}).
     */
    static final class DoubleSubmissionException extends RuntimeException {

        DoubleSubmissionException(String gmoOrderId) {
            super("GMO LinkType Plus double submission: " + gmoOrderId);
        }
    }
}
