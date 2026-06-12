package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCancellationRequestResponse {
    private UUID orderedItemId;
    private UUID orderId;
    private String itemName;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal price;
    private ItemStatus currentItemStatus;
    private String cancellationReason;
    private RequestStatus requestStatus;
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
    private List<OrderedItemModifierResponse> orderedItemModifiers;
    private UUID restaurantId;
    private String restaurantName;
}
