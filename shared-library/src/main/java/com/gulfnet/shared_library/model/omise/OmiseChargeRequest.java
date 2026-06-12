package com.gulfnet.shared_library.model.omise;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OmiseChargeRequest {
    
    @JsonProperty("amount")
    private Long amount; // Amount in smallest currency unit
    
    @JsonProperty("currency")
    private String currency; // Currency code
    
    @JsonProperty("source")
    private String source; // Source ID
    
    @JsonProperty("description")
    private String description; // Charge description
    
    @JsonProperty("metadata")
    private ChargeMetadata metadata; // Additional metadata
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeMetadata {
        @JsonProperty("order_id")
        private String orderId; // Order ID for reference
    }
}
