package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class ReceiptUrlResponse {
    private String orderId;
    private String orderNumber;
    private String receiptUrl;
    private String downloadReceiptUrl;
}
