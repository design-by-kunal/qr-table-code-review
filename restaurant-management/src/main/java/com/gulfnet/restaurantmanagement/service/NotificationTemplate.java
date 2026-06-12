package com.gulfnet.restaurantmanagement.service;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Map;

/**
 * Notification template for common FCM notification patterns
 */
@Data
@Builder
@SuppressWarnings({"java:S1068", "java:S1192"}) // Fields used via Lombok; literals reused intentionally for tags/sounds
public class NotificationTemplate {
    
    @NonNull
    private String templateId;
    
    @NonNull
    private String titleKey;
    
    @NonNull
    private String bodyKey;
    
    private Map<String, String> defaultData;
    
    private NotificationMessage.NotificationPriority priority;
    
    private NotificationMessage.NotificationType messageType;
    
    private Long timeToLive;
    
    private String sound;
    
    private String icon;
    
    private String color;
    
    private String tag;
    
    private String imageUrl;
    
    private String clickAction;
    
    private Boolean contentAvailable;
    
    private Boolean mutableContent;
    
    /**
     * Predefined notification templates
     */
    public static class Templates {

        // Sound constants
        private static final String SOUND_REQUEST_DECISION = "request_decision";
        private static final String SOUND_MANAGER_NOTIFICATION = "manager_notification";
        private static final String SOUND_REQUEST_NOTIFICATION = "request_notification";
        private static final String SOUND_ITEM_READY = "item_ready";
        private static final String SOUND_ALERT = "alert";

        // Icon constants
        private static final String ICON_ORDER = "ic_order";
        private static final String ICON_REQUEST = "ic_request";
        private static final String ICON_MANAGER = "ic_manager";
        private static final String ICON_CANCELLATION = "ic_cancellation";
        private static final String ICON_ALERT = "ic_alert";

        // Color constants
        private static final String COLOR_GREEN = "#4CAF50";
        private static final String COLOR_RED = "#F44336";
        private static final String COLOR_ORANGE = "#FF9800";
        private static final String COLOR_BLUE = "#2196F3";
        private static final String COLOR_PURPLE = "#9C27B0";

        private Templates() {
            // Prevent instantiation
        }
        
        public static final NotificationTemplate ORDER_PLACED = NotificationTemplate.builder()
                .templateId("ORDER_PLACED")
                .titleKey("notification.order.placed.title")
                .bodyKey("notification.order.placed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("order_placed")
                .icon(ICON_ORDER)
                .color(COLOR_GREEN)
                .tag("order_placed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** Line item PUSHED to kitchen — KDS body uses localized item name: {0} pushed to the kitchen. */
        public static final NotificationTemplate ITEM_PUSHED_TO_KITCHEN = NotificationTemplate.builder()
                .templateId("ITEM_PUSHED")
                .titleKey("notification.item.pushed.title")
                .bodyKey("notification.item.pushed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("order_placed")
                .icon(ICON_ORDER)
                .color(COLOR_GREEN)
                .tag("item_pushed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** KDS line item — COOKING ({0} = localized item name). */
        public static final NotificationTemplate KDS_ITEM_COOKING = NotificationTemplate.builder()
                .templateId("KDS_COOKING")
                .titleKey("notification.kds.cooking.title")
                .bodyKey("notification.kds.cooking.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound("order_updated")
                .icon(ICON_ORDER)
                .color(COLOR_ORANGE)
                .tag("kds_cooking")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** KDS line item — READY ({0} = localized item name). */
        public static final NotificationTemplate KDS_ITEM_READY = NotificationTemplate.builder()
                .templateId("KDS_READY")
                .titleKey("notification.kds.ready.title")
                .bodyKey("notification.kds.ready.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound(SOUND_ITEM_READY)
                .icon("ic_item_ready")
                .color(COLOR_GREEN)
                .tag("kds_ready")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** KDS line item — DELAYED ({0} = localized item name). */
        public static final NotificationTemplate KDS_ITEM_DELAYED = NotificationTemplate.builder()
                .templateId("KDS_DELAYED")
                .titleKey("notification.kds.delayed.title")
                .bodyKey("notification.kds.delayed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound("default")
                .icon(ICON_ORDER)
                .color("#FF9800")
                .tag("kds_delayed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ORDER_UPDATED = NotificationTemplate.builder()
                .templateId("ORDER_UPDATED")
                .titleKey("notification.order.updated.title")
                .bodyKey("notification.order.updated.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("order_updated")
                .icon(ICON_ORDER)
                .color(COLOR_ORANGE)
                .tag("order_updated")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ORDER_CANCELLED = NotificationTemplate.builder()
                .templateId("ORDER_CANCELLED")
                .titleKey("notification.order.cancelled.title")
                .bodyKey("notification.order.cancelled.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("order_cancelled")
                .icon(ICON_ORDER)
                .color(COLOR_RED)
                .tag("order_cancelled")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ORDER_UPDATE = NotificationTemplate.builder()
                .templateId("ORDER_UPDATE")
                .titleKey("notification.order.update.title")
                .bodyKey("notification.order.update.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("default")
                .icon(ICON_ORDER)
                .color("#FF5722")
                .tag("order_update")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ITEM_READY = NotificationTemplate.builder()
                .templateId("ITEM_READY")
                .titleKey("notification.item.ready.title")
                .bodyKey("notification.item.ready.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound(SOUND_ITEM_READY)
                .icon("ic_item_ready")
                .color(COLOR_GREEN)
                .tag("item_ready")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        /**
         * KDS line item — SERVED ({0} = localized item name), same copy pattern as KDS_COOKING / READY / DELAYED.
         * Visuals (sound/icon/color) are kept the same as ITEM_READY to preserve UX.
         */
        public static final NotificationTemplate ITEM_SERVED = NotificationTemplate.builder()
                .templateId("ITEM_SERVED")
                .titleKey("notification.kds.served.title")
                .bodyKey("notification.kds.served.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound(SOUND_ITEM_READY)
                .icon("ic_item_ready")
                .color(COLOR_GREEN)
                .tag("item_served")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ITEM_DELAYED = NotificationTemplate.builder()
                .templateId("ITEM_DELAYED")
                .titleKey("notification.item.delayed.title")
                .bodyKey("notification.item.delayed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("item_delayed")
                .icon("ic_item_delayed")
                .color(COLOR_ORANGE)
                .tag("item_delayed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ITEM_CANCELLED = NotificationTemplate.builder()
                .templateId("ITEM_CANCELLED")
                .titleKey("notification.item.cancelled.title")
                .bodyKey("notification.item.cancelled.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("item_cancelled")
                .icon(ICON_CANCELLATION)
                .color(COLOR_RED)
                .tag("item_cancelled")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate TABLE_ASSIGNED = NotificationTemplate.builder()
                .templateId("TABLE_ASSIGNED")
                .titleKey("notification.table.assigned.title")
                .bodyKey("notification.table.assigned.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("table_assigned")
                .icon("ic_table")
                .color(COLOR_BLUE)
                .tag("table_assigned")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate TABLE_ASSIGNED_TAKEAWAY = NotificationTemplate.builder()
                .templateId("TABLE_ASSIGNED")
                .titleKey("notification.table.assigned.title")
                .bodyKey("notification.table.assigned.takeaway.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("table_assigned")
                .icon("ic_table")
                .color(COLOR_BLUE)
                .tag("table_assigned")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PAYMENT_COMPLETED = NotificationTemplate.builder()
                .templateId("PAYMENT_COMPLETED")
                .titleKey("notification.payment.completed.title")
                .bodyKey("notification.payment.completed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound("payment_completed")
                .icon("ic_payment")
                .color(COLOR_GREEN)
                .tag("payment_completed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate CANCELLATION_APPROVED = NotificationTemplate.builder()
                .templateId("CANCELLATION_APPROVED")
                .titleKey("notification.cancellation.approved.title")
                .bodyKey("notification.cancellation.approved.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("cancellation_decision")
                .icon(ICON_CANCELLATION)
                .color(COLOR_RED)
                .tag("cancellation_approved")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate CANCELLATION_REJECTED = NotificationTemplate.builder()
                .templateId("CANCELLATION_REJECTED")
                .titleKey("notification.cancellation.rejected.title")
                .bodyKey("notification.cancellation.rejected.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("cancellation_decision")
                .icon(ICON_CANCELLATION)
                .color(COLOR_RED)
                .tag("cancellation_rejected")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate MANAGER_CANCEL_REQUEST_NOTIFICATION = NotificationTemplate.builder()
                .templateId("MANAGER_CANCEL_REQUEST_NOTIFICATION")
                .titleKey("manager.notification.cancel.request.title")
                .bodyKey("manager.notification.cancel.request.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon(ICON_MANAGER)
                .color(COLOR_PURPLE)
                .tag("manager_cancel_request_notification")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate MANAGER_ORDER_CANCEL_REQUEST_NOTIFICATION = NotificationTemplate.builder()
                .templateId("MANAGER_ORDER_CANCEL_REQUEST_NOTIFICATION")
                .titleKey("manager.notification.order.cancel.request.title")
                .bodyKey("manager.notification.order.cancel.request.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon(ICON_MANAGER)
                .color(COLOR_PURPLE)
                .tag("manager_order_cancel_request_notification")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        /**
         * Manager notification for transaction cancellation requests
         * Uses its own template so that title/body reflect "Transaction Cancellation Request"
         * instead of the generic item cancellation copy.
         */
        public static final NotificationTemplate MANAGER_TRANSACTION_CANCEL_REQUEST_NOTIFICATION = NotificationTemplate.builder()
                .templateId("MANAGER_TRANSACTION_CANCEL_REQUEST_NOTIFICATION")
                .titleKey("manager.notification.transaction.cancel.request.title")
                .bodyKey("manager.notification.transaction.cancel.request.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon(ICON_MANAGER)
                .color(COLOR_PURPLE)
                .tag("manager_transaction_cancel_request_notification")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        /**
         * Manager notification for refund requests
         */
        public static final NotificationTemplate MANAGER_REFUND_REQUEST_NOTIFICATION = NotificationTemplate.builder()
                .templateId("MANAGER_REFUND_REQUEST_NOTIFICATION")
                .titleKey("manager.notification.refund.request.title")
                .bodyKey("manager.notification.refund.request.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon(ICON_MANAGER)
                .color(COLOR_PURPLE)
                .tag("manager_refund_request_notification")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate MANAGER_ITEM_CANCELED_NOTIFICATION = NotificationTemplate.builder()
                .templateId("MANAGER_ITEM_CANCELED_NOTIFICATION")
                .titleKey("manager.notification.item.canceled.title")
                .bodyKey("manager.notification.item.canceled.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon(ICON_MANAGER)
                .color(COLOR_PURPLE)
                .tag("manager_item_canceled_notification")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate TABLE_REMOVED = NotificationTemplate.builder()
                .templateId("TABLE_REMOVED")
                .titleKey("notification.table.removed.title")
                .bodyKey("notification.table.removed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("table_removed")
                .icon("ic_table")
                .color(COLOR_BLUE)
                .tag("table_removed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PASSWORD_UPDATED = NotificationTemplate.builder()
                .templateId("PASSWORD_UPDATED")
                .titleKey("notification.password.updated.title")
                .bodyKey("notification.password.updated.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("password_updated")
                .icon("ic_password")
                .color(COLOR_GREEN)
                .tag("password_updated")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate TABLE_SECTION_REQUEST_OPENED = NotificationTemplate.builder()
                .templateId("TABLE_SECTION_REQUEST_OPENED")
                .titleKey("notification.table.section.request.opened.title")
                .bodyKey("notification.table.section.request.opened.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_REQUEST_NOTIFICATION)
                .icon(ICON_REQUEST)
                .color(COLOR_PURPLE)
                .tag("table_section_request_opened")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate TABLE_SECTION_REQUEST_CREATED = NotificationTemplate.builder()
                .templateId("TABLE_SECTION_REQUEST_CREATED")
                .titleKey("notification.table.section.request.created.title")
                .bodyKey("notification.table.section.request.created.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("request_confirmation")
                .icon(ICON_REQUEST)
                .color(COLOR_GREEN)
                .tag("table_section_request_created")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PROFILE_UPDATE_REQUEST_OPENED = NotificationTemplate.builder()
                .templateId("PROFILE_UPDATE_REQUEST_OPENED")
                .titleKey("notification.profile.update.request.opened.title")
                .bodyKey("notification.profile.update.request.opened.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_REQUEST_NOTIFICATION)
                .icon(ICON_REQUEST)
                .color(COLOR_PURPLE)
                .tag("profile_update_request_opened")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PROFILE_UPDATE_REQUEST_CREATED = NotificationTemplate.builder()
                .templateId("PROFILE_UPDATE_REQUEST_CREATED")
                .titleKey("notification.profile.update.request.created.title")
                .bodyKey("notification.profile.update.request.created.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("request_confirmation")
                .icon(ICON_REQUEST)
                .color(COLOR_GREEN)
                .tag("profile_update_request_created")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PROFILE_UPDATE_REQUEST_APPROVED = NotificationTemplate.builder()
                .templateId("PROFILE_UPDATE_REQUEST_APPROVED")
                .titleKey("notification.profile.update.request.approved.title")
                .bodyKey("notification.profile.update.request.approved.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_GREEN)
                .tag("profile_update_request_approved")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PROFILE_UPDATE_REQUEST_DECLINED = NotificationTemplate.builder()
                .templateId("PROFILE_UPDATE_REQUEST_DECLINED")
                .titleKey("notification.profile.update.request.declined.title")
                .bodyKey("notification.profile.update.request.declined.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_RED)
                .tag("profile_update_request_declined")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PROFILE_UPDATED_DIRECTLY = NotificationTemplate.builder()
                .templateId("PROFILE_UPDATED_DIRECTLY")
                .titleKey("notification.profile.updated.directly.title")
                .bodyKey("notification.profile.updated.directly.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound("profile_update")
                .icon("ic_profile")
                .color(COLOR_BLUE)
                .tag("profile_updated_directly")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate EMPLOYEE_ASSIGNED_TO_RESTAURANT = NotificationTemplate.builder()
                .templateId("EMPLOYEE_ASSIGNED_TO_RESTAURANT")
                .titleKey("notification.employee.assigned.to.restaurant.title")
                .bodyKey("notification.employee.assigned.to.restaurant.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound("employee_assigned")
                .icon("ic_employee")
                .color(COLOR_GREEN)
                .tag("employee_assigned_to_restaurant")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate ADDITIONAL_DISCOUNT_REQUEST_OPENED = NotificationTemplate.builder()
                .templateId("ADDITIONAL_DISCOUNT_REQUEST_OPENED")
                .titleKey("notification.additional.discount.request.opened.title")
                .bodyKey("notification.additional.discount.request.opened.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_REQUEST_NOTIFICATION)
                .icon(ICON_REQUEST)
                .color(COLOR_PURPLE)
                .tag("additional_discount_request_opened")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate PAYMENT_ERROR = NotificationTemplate.builder()
                .templateId("PAYMENT_ERROR")
                .titleKey("notification.payment.error.title")
                .bodyKey("notification.payment.error.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound("payment_error")
                .icon("ic_payment_error")
                .color(COLOR_RED)
                .tag("payment_error")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate PAYMENT_FAILED = NotificationTemplate.builder()
                .templateId("PAYMENT_FAILED")
                .titleKey("notification.payment.failed.title")
                .bodyKey("notification.payment.failed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound("payment_error")
                .icon("ic_payment_error")
                .color(COLOR_RED)
                .tag("payment_failed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate PAYMENT_EXPIRED = NotificationTemplate.builder()
                .templateId("PAYMENT_EXPIRED")
                .titleKey("notification.payment.expired.title")
                .bodyKey("notification.payment.expired.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound("payment_error")
                .icon("ic_payment_error")
                .color(COLOR_RED)
                .tag("payment_expired")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate DISCOUNT_REQUEST_APPROVED = NotificationTemplate.builder()
                .templateId("DISCOUNT_REQUEST_APPROVED")
                .titleKey("notification.discount.request.approved.title")
                .bodyKey("notification.discount.request.approved.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon("ic_discount")
                .color(COLOR_GREEN)
                .tag("discount_request_approved")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate DISCOUNT_REQUEST_DECLINED = NotificationTemplate.builder()
                .templateId("DISCOUNT_REQUEST_DECLINED")
                .titleKey("notification.discount.request.declined.title")
                .bodyKey("notification.discount.request.declined.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon("ic_discount")
                .color(COLOR_RED)
                .tag("discount_request_declined")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate REFUND_REQUEST_APPROVED = NotificationTemplate.builder()
                .templateId("REFUND_REQUEST_APPROVED")
                .titleKey("notification.refund.request.approved.title")
                .bodyKey("notification.refund.request.approved.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon("ic_refund")
                .color(COLOR_GREEN)
                .tag("refund_request_approved")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate REFUND_REQUEST_DECLINED = NotificationTemplate.builder()
                .templateId("REFUND_REQUEST_DECLINED")
                .titleKey("notification.refund.request.declined.title")
                .bodyKey("notification.refund.request.declined.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon("ic_refund")
                .color(COLOR_RED)
                .tag("refund_request_declined")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate DEVICE_INTEGRATION_ERROR = NotificationTemplate.builder()
                .templateId("DEVICE_INTEGRATION_ERROR")
                .titleKey("notification.device.integration.error.title")
                .bodyKey("notification.device.integration.error.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(1800L) // 30 minutes
                .sound("device_error")
                .icon("ic_device_error")
                .color(COLOR_RED)
                .tag("device_integration_error")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST")
                .titleKey("notification.cash.drawer.shift.discrepancy.request.title")
                .bodyKey("notification.cash.drawer.shift.discrepancy.request.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_REQUEST_NOTIFICATION)
                .icon(ICON_REQUEST)
                .color(COLOR_PURPLE)
                .tag("cash_drawer_shift_discrepancy_request")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED")
                .titleKey("notification.cash.drawer.shift.discrepancy.approved.title")
                .bodyKey("notification.cash.drawer.shift.discrepancy.approved.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_GREEN)
                .tag("cash_drawer_shift_discrepancy_approved")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
        
        public static final NotificationTemplate CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED")
                .titleKey("notification.cash.drawer.shift.discrepancy.declined.title")
                .bodyKey("notification.cash.drawer.shift.discrepancy.declined.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_RED)
                .tag("cash_drawer_shift_discrepancy_declined")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /**
         * ==================== HQ ADMIN ALERT NOTIFICATIONS ====================
         *
         * These templates are used for system-level alerts sent to HQ Admin:
         * - Sales threshold reached
         * - Refund percentage exceeded
         * - Cancellation percentage exceeded
         */

        public static final NotificationTemplate SALES_THRESHOLD_ALERT = NotificationTemplate.builder()
                .templateId("SALES_THRESHOLD_ALERT")
                .titleKey("notification.alert.sales.threshold.title")
                .bodyKey("notification.alert.sales.threshold.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_ALERT)
                .icon(ICON_ALERT)
                .color(COLOR_ORANGE)
                .tag("sales_threshold_alert")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate REFUND_PERCENTAGE_ALERT = NotificationTemplate.builder()
                .templateId("REFUND_PERCENTAGE_ALERT")
                .titleKey("notification.alert.refund.percentage.title")
                .bodyKey("notification.alert.refund.percentage.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_ALERT)
                .icon(ICON_ALERT)
                .color(COLOR_RED)
                .tag("refund_percentage_alert")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** Order cancellation % (order-based: SERVED vs CANCELED orders). */
        public static final NotificationTemplate ORDER_CANCELLATION_PERCENTAGE_ALERT = NotificationTemplate.builder()
                .templateId("ORDER_CANCELLATION_PERCENTAGE_ALERT")
                .titleKey("notification.alert.order.cancellation.percentage.title")
                .bodyKey("notification.alert.order.cancellation.percentage.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound(SOUND_ALERT)
                .icon(ICON_ALERT)
                .color("#FF5722")
                .tag("order_cancellation_percentage_alert")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** Transaction cancellation % (transaction-based: COMPLETED vs CANCELED). */
        public static final NotificationTemplate TRANSACTION_CANCELLATION_PERCENTAGE_ALERT = NotificationTemplate.builder()
                .templateId("TRANSACTION_CANCELLATION_PERCENTAGE_ALERT")
                .titleKey("notification.alert.transaction.cancellation.percentage.title")
                .bodyKey("notification.alert.transaction.cancellation.percentage.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound(SOUND_ALERT)
                .icon(ICON_ALERT)
                .color("#FF5722")
                .tag("transaction_cancellation_percentage_alert")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** Single push when both order and transaction cancellation % are breached (avoids duplicate popups). */
        public static final NotificationTemplate CANCELLATION_PERCENTAGE_COMBINED_ALERT = NotificationTemplate.builder()
                .templateId("CANCELLATION_PERCENTAGE_COMBINED_ALERT")
                .titleKey("notification.alert.cancellation.percentage.combined.title")
                .bodyKey("notification.alert.cancellation.percentage.combined.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L)
                .sound(SOUND_ALERT)
                .icon(ICON_ALERT)
                .color("#FF5722")
                .tag("cancellation_percentage_alert")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /**
         * @deprecated Prefer {@link #ORDER_CANCELLATION_PERCENTAGE_ALERT} or {@link #TRANSACTION_CANCELLATION_PERCENTAGE_ALERT}.
         * Retained as an alias for existing references; remove in a coordinated API cleanup when callers are migrated.
         */
        @Deprecated
        public static final NotificationTemplate CANCELLATION_PERCENTAGE_ALERT = TRANSACTION_CANCELLATION_PERCENTAGE_ALERT;

        public static final NotificationTemplate CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER")
                .titleKey("notification.cash.drawer.shift.closed.by.manager.title")
                .bodyKey("notification.cash.drawer.shift.closed.by.manager.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_BLUE)
                .tag("cash_drawer_shift_closed_by_manager")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate CASH_DRAWER_SHIFT_STARTED = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_STARTED")
                .titleKey("notification.cash.drawer.shift.started.title")
                .bodyKey("notification.cash.drawer.shift.started.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_GREEN)
                .tag("cash_drawer_shift_started")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        public static final NotificationTemplate CASH_DRAWER_SHIFT_CLOSED = NotificationTemplate.builder()
                .templateId("CASH_DRAWER_SHIFT_CLOSED")
                .titleKey("notification.cash.drawer.shift.closed.title")
                .bodyKey("notification.cash.drawer.shift.closed.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(3600L) // 1 hour
                .sound(SOUND_REQUEST_DECISION)
                .icon(ICON_REQUEST)
                .color(COLOR_ORANGE)
                .tag("cash_drawer_shift_closed")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /**
         * ==================== MENU ASSIGNMENT NOTIFICATIONS ====================
         *
         * Sent to restaurant managers when a new menu is assigned to their restaurant
         * so they can update KDS device assignments.
         */
        public static final NotificationTemplate MENU_ASSIGNED_TO_RESTAURANT = NotificationTemplate.builder()
                .templateId("MENU_ASSIGNED_TO_RESTAURANT")
                .titleKey("notification.menu.assigned.to.restaurant.title")
                .bodyKey("notification.menu.assigned.to.restaurant.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L) // 2 hours
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon("ic_menu")
                .color(COLOR_BLUE)
                .tag("menu_assigned_to_restaurant")
                .contentAvailable(true)
                .mutableContent(false)
                .build();

        /** Managers: menu is live for their restaurant; default KDS synced, other KDS need manual updates. */
        public static final NotificationTemplate MENU_LIVE_AT_RESTAURANT = NotificationTemplate.builder()
                .templateId("MENU_LIVE_AT_RESTAURANT")
                .titleKey("notification.menu.live.at.restaurant.title")
                .bodyKey("notification.menu.live.at.restaurant.body")
                .priority(NotificationMessage.NotificationPriority.HIGH)
                .messageType(NotificationMessage.NotificationType.NOTIFICATION_WITH_DATA)
                .timeToLive(7200L)
                .sound(SOUND_MANAGER_NOTIFICATION)
                .icon("ic_menu")
                .color(COLOR_BLUE)
                .tag("menu_live_at_restaurant")
                .contentAvailable(true)
                .mutableContent(false)
                .build();
    }
}
