package com.gulfnet.shared_library.model.response;

import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    // Fields from PaymentRequest
    private UUID orderId;
    private String paymentMethod;
    /** UPI wallet type (paypay, promptpay, paynow) or CASH / CARD when applicable. */
    private String paymentApp;
    private BigDecimal amountPaid;
    private BigDecimal cashReceived;
    private BigDecimal changeReturned;
    private UUID transactionId;
    private String transactionNumber;
    private TransactionStatus transactionStatus;
    private String receiptUrl;
    
    // Omise-specific fields (for UPI payments)
    private String sourceId; // Omise source ID
    private String chargeId; // Omise charge ID
    private String qrCode; // PayPay: base64 | PayNow: data:image/png;base64,... | PromptPay: short http URL to .../omise-qr
    private String authorizationUri; // Authorization URI for PayPay (frontend generates QR code from this URI)

    /** GMO LinkType Plus hosted checkout URL (credit card). */
    private String linkUrl;

    /** GMO {@code OrderID} sent to LinkType Plus (from {@code Order.gmoLinkOrderId}). */
    private String gmoLinkOrderId;
    
    // Restaurant ID for WebSocket subscription
    private UUID restaurantId;
}
