package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailsResponse {
    // Ordered Item Info
    private UUID orderedItemId;
    private UUID itemId;
    private String itemName;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalItemAmount;
    private ItemStatus itemStatus;
    private String reason;
    private String notes; // Guest instructions
    private OffsetDateTime itemCreatedAt; // When item was first created (PUSHED status)
    private OffsetDateTime itemUpdatedAt; // Last status update
    
    // Modifications
    private List<OrderedItemModifierResponse> orderedItemModifiers;
    
    // Order Info
    private UUID orderId;
    private String orderNumber;
    private OrderStatus orderStatus;
    private OrderType orderType; // DINE_IN / TAKEAWAY
    private OffsetDateTime orderPlacedAt; // Time order was placed
    private BigDecimal orderTotalAmount;
    
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
    private String lastStatusChangedBy; // Name of user who last updated status
    
    // Status timeline (simplified - just timestamps, no full history)
    private OffsetDateTime statusChangedAt; // Same as itemUpdatedAt
    private String statusChangedBy; // Same as lastStatusChangedBy
}
