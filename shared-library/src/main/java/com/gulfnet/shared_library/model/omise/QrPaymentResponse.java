package com.gulfnet.shared_library.model.omise;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrPaymentResponse {
    
    private String chargeId;
    private String qrUrl; // QR code (base64 data URL for PayPay; Omise download_uri for PromptPay/PayNow)
    private String authorizeUri; // Authorization URI (PayPay) or download URI (PromptPay/PayNow)
    private String status;
}
