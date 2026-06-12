package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for building FCM notifications using templates and localized messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBuilderService {

    private static final String KEY_ORDER_ID = "orderId";
    private static final String KEY_TABLE_NUMBER = "tableNumber";
    private static final String KEY_ADDITIONAL_INFO = "additionalInfo";
    
    private final MessageUtil messageUtil;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * Build FCM message from template with localized content.
     * Also preserves the body message key and arguments so they can be stored
     * alongside the resolved text for locale-aware re-resolution at read time.
     *
     * @param template Notification template
     * @param userLocale User's locale
     * @param messageArgs Arguments for message formatting
     * @param additionalData Additional data to include
     * @return Built FCM message
     */
    public NotificationMessage buildMessage(NotificationTemplate template, Locale userLocale, 
                                 Object[] messageArgs, Map<String, String> additionalData) {
        
        String title = messageUtil.getMessage(template.getTitleKey(), userLocale, messageArgs);
        String body = messageUtil.getMessage(template.getBodyKey(), userLocale, messageArgs);
        
        Map<String, String> data = new HashMap<>();
        
        // Add default data from template
        if (template.getDefaultData() != null) {
            data.putAll(template.getDefaultData());
        }
        
        // Add additional data
        if (additionalData != null) {
            data.putAll(additionalData);
        }
        
        // Add common metadata
        data.put("templateId", template.getTemplateId());
        data.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        data.put("locale", userLocale.getLanguage());

        String serializedArgs = serializeBodyArgs(messageArgs);
        
        return NotificationMessage.builder()
                .title(title)
                .body(body)
                .bodyKey(template.getBodyKey())
                .bodyArgs(serializedArgs)
                .data(data)
                .priority(template.getPriority())
                .messageType(template.getMessageType())
                .timeToLive(template.getTimeToLive())
                .sound(template.getSound())
                .icon(template.getIcon())
                .color(template.getColor())
                .tag(template.getTag())
                .imageUrl(template.getImageUrl())
                .clickAction(template.getClickAction())
                .contentAvailable(template.getContentAvailable())
                .mutableContent(template.getMutableContent())
                .build();
    }

    /**
     * Serializes notification body arguments into a JSON string array.
     * <p>
     * Converts each argument to {@link String} via {@code toString()} (null becomes empty string) and serializes the
     * resulting array using the shared object mapper. Returns {@code null} when there are no arguments or serialization
     * fails.
     * </p>
     *
     * @param args message formatting arguments (nullable)
     * @return JSON array string of arguments, or {@code null} when absent/unserializable
     */
    static String serializeBodyArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String[] stringArgs = Arrays.stream(args)
                    .map(a -> a != null ? a.toString() : "")
                    .toArray(String[]::new);
            return OBJECT_MAPPER.writeValueAsString(stringArgs);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification body args: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Build notification message from template with current locale
     * @param template Notification template
     * @param messageArgs Arguments for message formatting
     * @param additionalData Additional data to include
     * @return Built notification message
     */
    public NotificationMessage buildMessage(NotificationTemplate template, Object[] messageArgs, Map<String, String> additionalData) {
        return buildMessage(template, LocaleContextHolder.getLocale(), messageArgs, additionalData);
    }
    
    /**
     * Build notification message from template with no additional data
     * @param template Notification template
     * @param userLocale User's locale
     * @param messageArgs Arguments for message formatting
     * @return Built notification message
     */
    public NotificationMessage buildMessage(NotificationTemplate template, Locale userLocale, Object[] messageArgs) {
        return buildMessage(template, userLocale, messageArgs, null);
    }
    
    /**
     * Build notification message from template with current locale and no additional data
     * @param template Notification template
     * @param messageArgs Arguments for message formatting
     * @return Built notification message
     */
    public NotificationMessage buildMessage(NotificationTemplate template, Object[] messageArgs) {
        return buildMessage(template, LocaleContextHolder.getLocale(), messageArgs, null);
    }
    
    /**
     * Build notification message from template with no arguments
     * @param template Notification template
     * @param userLocale User's locale
     * @return Built notification message
     */
    public NotificationMessage buildMessage(NotificationTemplate template, Locale userLocale) {
        return buildMessage(template, userLocale, new Object[0], null);
    }
    
    /**
     * Build notification message from template with current locale and no arguments
     * @param template Notification template
     * @return Built notification message
     */
    public NotificationMessage buildMessage(NotificationTemplate template) {
        return buildMessage(template, LocaleContextHolder.getLocale(), new Object[0], null);
    }
    
    /**
     * Build data map for item-related notifications
     * @param orderedItemId Ordered item ID
     * @param itemId Item ID
     * @param itemName Item name
     * @param orderId Order ID
     * @param tableNumber Table number
     * @param itemStatus Item status
     * @param quantity Quantity
     * @param additionalInfo Additional information
     * @return Data map
     */
    public Map<String, String> buildItemData(String orderedItemId, String itemId, String itemName, 
                                           String orderId, String tableNumber, String itemStatus, 
                                           String quantity, String additionalInfo) {
        Map<String, String> data = new HashMap<>();
        data.put("orderedItemId", orderedItemId);
        data.put("itemId", itemId);
        data.put("itemName", itemName);
        data.put(KEY_ORDER_ID, orderId);
        data.put(KEY_TABLE_NUMBER, tableNumber);
        data.put("itemStatus", itemStatus);
        data.put("quantity", quantity);
        
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
        }
        
        return data;
    }
    
    /**
     * Build data map for table-related notifications
     * @param tableId Table ID
     * @param tableNumber Table number
     * @param tableStatus Table status
     * @param sectionId Section ID
     * @param sectionName Section name
     * @return Data map
     */
    public Map<String, String> buildTableData(String tableId, String tableNumber, String tableStatus, 
                                             String sectionId, String sectionName) {
        Map<String, String> data = new HashMap<>();
        data.put("tableId", tableId);
        data.put(KEY_TABLE_NUMBER, tableNumber);
        data.put("tableStatus", tableStatus);
        
        if (sectionId != null) {
            data.put("sectionId", sectionId);
        }
        if (sectionName != null) {
            data.put("sectionName", sectionName);
        }
        
        return data;
    }
    
    /**
     * Build data map for order-related notifications
     * @param orderId Order ID
     * @param tableNumber Table number
     * @param orderStatus Order status
     * @param totalAmount Total amount
     * @param additionalInfo Additional information
     * @return Data map
     */
    public Map<String, String> buildOrderData(String orderId, String tableNumber, String orderStatus, 
                                             String totalAmount, String additionalInfo) {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_ORDER_ID, orderId);
        data.put(KEY_TABLE_NUMBER, tableNumber);
        data.put("orderStatus", orderStatus);
        
        if (totalAmount != null) {
            data.put("totalAmount", totalAmount);
        }
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
        }
        
        return data;
    }
    
    /**
     * Build data map for payment-related notifications
     * @param orderId Order ID
     * @param tableNumber Table number
     * @param paymentMethod Payment method
     * @param amountPaid Amount paid
     * @param additionalInfo Additional information
     * @return Data map
     */
    public Map<String, String> buildPaymentData(String orderId, String tableNumber, String paymentMethod, 
                                               String amountPaid, String additionalInfo) {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_ORDER_ID, orderId);
        data.put(KEY_TABLE_NUMBER, tableNumber);
        data.put("paymentMethod", paymentMethod);
        data.put("amountPaid", amountPaid);
        
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
        }
        
        return data;
    }
    
    /**
     * Build data map for user-related notifications
     * @param userId User ID
     * @param userName User name
     * @param userRole User role
     * @param additionalInfo Additional information
     * @return Data map
     */
    public Map<String, String> buildUserData(String userId, String userName, String userRole, String additionalInfo) {
        Map<String, String> data = new HashMap<>();
        data.put("userId", userId);
        data.put("userName", userName);
        data.put("userRole", userRole);
        
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
        }
        
        return data;
    }
}
