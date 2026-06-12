package com.gulfnet.shared_library.enums;

/**
 * Enum representing the types of actions that can be logged in audit trails.
 * This enum covers all system actions including authentication, transactions,
 * CRUD operations, and system-level changes.
 */
public enum ActionType {
    // Authentication actions
    LOGIN,
    LOGOUT,
    
    // Transaction actions
    PAYMENT,
    REFUND,
    CANCELLATION,
    
    // Order actions
    ORDER_MODIFICATION,
    
    // Discount and Promotion actions
    DISCOUNT,
    DISCOUNT_CREATE,
    DISCOUNT_UPDATE,
    DISCOUNT_DELETE,
    PROMOTION_CREATE,
    PROMOTION_UPDATE,
    PROMOTION_DELETE,
    
    // Restaurant management actions
    RESTAURANT_CREATE,
    RESTAURANT_UPDATE,
    RESTAURANT_DELETE,
    RESTAURANT_GROUP_CREATE,
    RESTAURANT_GROUP_UPDATE,
    RESTAURANT_GROUP_DELETE,
    
    // Menu management actions
    MENU_CREATE,
    MENU_UPDATE,
    MENU_DELETE,
    MENU_PUBLISH,
    MENU_UNPUBLISH,
    MENU_ITEM_CREATE,
    MENU_ITEM_UPDATE,
    MENU_ITEM_DELETE,
    CATEGORY_CREATE,
    CATEGORY_UPDATE,
    CATEGORY_DELETE,
    MODIFIER_CREATE,
    MODIFIER_UPDATE,
    MODIFIER_DELETE,
    PRICE_OVERRIDE_CREATE,
    PRICE_OVERRIDE_UPDATE,
    PRICE_OVERRIDE_DELETE,
    PRICE_OVERRIDE_ACTIVATE,
    
    // Table management actions
    TABLE_CREATE,
    TABLE_UPDATE,
    TABLE_DELETE,
    SECTION_CREATE,
    SECTION_UPDATE,
    SECTION_DELETE,
    TABLE_LAYOUT_TEMPLATE_CREATE,
    TABLE_LAYOUT_TEMPLATE_UPDATE,
    TABLE_LAYOUT_TEMPLATE_DELETE,
    
    // User management actions
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,
    ROLE_CREATE,
    ROLE_UPDATE,
    ROLE_DELETE,
    
    // KDS management actions
    KDS_CREATE,
    KDS_UPDATE,
    KDS_DELETE,
    
    // Settings actions
    SETTINGS_UPDATE,
    
    // Manager-specific Employee Management actions
    EMPLOYEE_BULK_UPLOAD,
    EMPLOYEE_STATUS_UPDATE,
    
    // Manager-specific Table Management actions
    TABLE_TRANSFER,
    TABLE_BLOCK,
    TABLE_UNBLOCK,
    TABLE_MOVE_SECTION,
    TABLE_CAPACITY_UPDATE,
    TABLE_WAITER_ASSIGN,
    TABLE_WAITER_UNASSIGN,
    TABLE_QR_GENERATE,
    TABLE_QR_PRINT,
    
    // Manager-specific Request Management actions
    REQUEST_CANCEL_ITEM_APPROVE,
    REQUEST_CANCEL_ITEM_DECLINE,
    REQUEST_CANCEL_TRANSACTION_APPROVE,
    REQUEST_CANCEL_TRANSACTION_DECLINE,
    REQUEST_ADDITIONAL_DISCOUNT_APPROVE,
    REQUEST_ADDITIONAL_DISCOUNT_DECLINE,
    REQUEST_REFUND_APPROVE,
    REQUEST_REFUND_DECLINE,
    REQUEST_SHIFT_DISCREPANCY_APPROVE,
    REQUEST_SHIFT_DISCREPANCY_DECLINE,
    REQUEST_PROFILE_UPDATE_APPROVE,
    REQUEST_PROFILE_UPDATE_DECLINE,
    
    // Manager-specific Promotion actions
    PROMOTION_MODIFY,
    PROMOTION_ACTIVATE,
    PROMOTION_DEACTIVATE,
    
    // Manager-specific Discount actions
    DISCOUNT_MODIFY,
    DISCOUNT_ACTIVATE,
    DISCOUNT_DEACTIVATE,
    
    // Manager-specific Kitchen Management actions
    ORDER_CANCEL,
    ORDER_DISCOUNT_ADD,
    ITEM_AVAILABILITY_UPDATE,
    KDS_ASSIGNEE_ADD,
    KDS_ASSIGNEE_REMOVE,
    
    // Manager-specific Settings actions
    KDS_RESET_TIME_EXTEND,
    
    // Manager-specific Report actions
    REPORT_EXPORT,
    
    // Generic system actions
    SYSTEM_ACTION
}

