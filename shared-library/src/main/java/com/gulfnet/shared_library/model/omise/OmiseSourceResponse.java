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
public class OmiseSourceResponse {
    
    @JsonProperty("id")
    private String id; // Source ID
    
    @JsonProperty("type")
    private String type; // Source type
    
    @JsonProperty("flow")
    private String flow; // Flow type (e.g., "redirect", "offline")
    
    @JsonProperty("amount")
    private Long amount; // Amount in smallest currency unit
    
    @JsonProperty("currency")
    private String currency; // Currency code
    
    @JsonProperty("scannable_code")
    private ScannableCode scannableCode; // QR code information
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScannableCode {
        @JsonProperty("image")
        private Image image; // QR code image
        
        @JsonProperty("type")
        private String type; // Code type (e.g., "qr_code")
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Image {
            @JsonProperty("download_uri")
            private String downloadUri; // QR code image URL
        }
    }
}
