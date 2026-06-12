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
public class OmiseChargeResponse {
    
    @JsonProperty("id")
    private String id; // Charge ID
    
    @JsonProperty("status")
    private String status; // Charge status (e.g., "pending", "successful", "failed")
    
    @JsonProperty("amount")
    private Long amount; // Amount in smallest currency unit
    
    @JsonProperty("currency")
    private String currency; // Currency code
    
    @JsonProperty("source")
    private SourceInfo source; // Source information
    
    @JsonProperty("authorize_uri")
    private String authorizeUri; // Authorization URI (if applicable)
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        @JsonProperty("id")
        private String id; // Source ID
        
        @JsonProperty("type")
        private String type; // Source type
    }
}
