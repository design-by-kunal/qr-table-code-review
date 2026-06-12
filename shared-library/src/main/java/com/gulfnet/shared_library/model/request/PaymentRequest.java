package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    @NotNull
    private UUID orderId;
    
    @NotBlank
    private String paymentMethod;
    
    @NotNull
    private BigDecimal amountPaid;

    /**
     * CASH only: physical cash received from customer (tendered amount).
     * If null for CASH, backend will default to amountPaid for backward compatibility.
     */
    private BigDecimal cashReceived;

    /**
     * CASH only: physical change returned to customer.
     * If null for CASH, backend will compute as max(cashReceived - orderTotal, 0).
     */
    private BigDecimal changeReturned;
    
    @Email
    private String email; // Optional field for sending receipt
    
    /**
     * UPI only: wallet/app type (e.g. paypay, promptpay, paynow). Stored on transaction.payment_app.
     * Omit for CASH (saved as CASH) and CARD (saved as CARD).
     */
    private String type;

    /**
     * GMO LinkType Plus hosted credit card (CARD / CREDIT_CARD / DEBIT_CARD): return URLs for customer browser.
     */
    private String retUrl;

    private String completeUrl;

    private String cancelUrl;

    /**
     * {@code "0"} or {@code "1"} for GMO {@code ResultSkipFlag}; omit to default to {@code "1"} server-side.
     */
    private String resultSkipFlag;

}
