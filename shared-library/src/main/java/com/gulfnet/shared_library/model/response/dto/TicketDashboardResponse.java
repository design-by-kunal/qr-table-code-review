package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ItemStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDashboardResponse {
    private UUID orderedItemId;
    private UUID orderId;
    private String orderNumber;
    private UUID itemId;
    private String itemName;
    private String imageUrl;
    private Integer quantity;
    private ItemStatus itemStatus;
    private String reason;
    private String notes; // Guest instructions
    private OffsetDateTime orderPlacedAt; // Time order was placed (order.createdAt)
    private OffsetDateTime itemCreatedAt; // When item was created (first status: PUSHED)
    private OffsetDateTime itemUpdatedAt; // Last status update time
    
    // Modifications
    private List<OrderedItemModifierResponse> orderedItemModifiers;
    
    // Table & Section Info
    private UUID tableId;
    private String tableCode;
    private Integer tableOrder;
    private Integer rowOrder;
    private UUID sectionId;
    private String sectionName;
    
    // Assigned Waiter Info
    private UUID waiterId;
    private String waiterName;
    
    // Service Type
    private String serviceType; // DINE_IN or TAKEAWAY
    
    // Category Info (for filtering)
    private UUID categoryId;
    private String categoryName;
    private UUID subcategoryId;
    private String subcategoryName;
    
    // Status timeline (simplified - just current status with timestamps)
    private String lastStatusChangedBy;
}
