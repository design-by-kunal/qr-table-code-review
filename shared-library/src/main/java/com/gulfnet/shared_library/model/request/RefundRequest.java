package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotEmpty(message = "{refund.request.items.required}")
    private List<RefundItemRequest> items; // Items to refund (full or partial)

    @NotBlank(message = "{refund.request.reason.required}")
    private String refundReason; // Mandatory refund reason

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundItemRequest {
        @NotNull(message = "{refund.request.item.id.required}")
        private UUID itemId; // OrderedItem or OrderedCombo ID

        @NotBlank(message = "{refund.request.item.type.required}")
        private String itemType; // "ITEM" or "COMBO"

        @NotNull(message = "{refund.request.quantity.required}")
        private Integer quantity; // Quantity to refund (for partial refunds)
    }
}

