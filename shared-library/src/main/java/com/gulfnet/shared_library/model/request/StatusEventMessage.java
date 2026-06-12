package com.gulfnet.shared_library.model.request;

import lombok.*;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusEventMessage  {
    /** Localized short heading for pop-ups; clients should prefer this over English labels derived from {@link #notificationType}. */
    private String title;
    private String message; // Message for frontend to display/trigger actions
    private String notificationType; // Type of notification: NEW_ORDER, ITEM_CANCELED, ORDER_CANCELED, PROFILE_UPDATE, etc.
    private String orderId; // Order ID if applicable
    private String itemId; // Item/Combo ID if applicable
    private String userId; // User ID if applicable
    private String status; // Status value if applicable
    private Map<String, Object> data; // Additional data for the notification
}
