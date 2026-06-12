package com.gulfnet.shared_library.model.omise;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OmiseWebhookEvent {
    
    @JsonProperty("id")
    private String id; // Event ID
    
    @JsonProperty("key")
    private String key; // Event key (e.g., "charge.create", "charge.complete")
    
    @JsonProperty("data")
    private ChargeData data; // Charge or refund data (structure is compatible for both)
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeData {
        @JsonProperty("id")
        private String id; // Charge ID
        
        @JsonProperty("status")
        private String status; // Charge status
        
        @JsonProperty("amount")
        private Long amount; // Amount in smallest currency unit
        
        @JsonProperty("currency")
        private String currency; // Currency code
        
        // Present for refund webhooks – this is the Omise charge ID
        @JsonProperty("charge")
        private String charge; // Charge ID for refund events
        
        @JsonProperty("source")
        private SourceInfo source; // Source information
        
        @JsonProperty("metadata")
        private ChargeMetadata metadata; // Metadata containing order ID
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SourceInfo {
            @JsonProperty("id")
            private String id; // Source ID
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ChargeMetadata {
            @JsonProperty("orderId")
            @JsonAlias({"order_id"}) // Support both camelCase and snake_case
            private String orderId; // Order ID
        }
    }
}
