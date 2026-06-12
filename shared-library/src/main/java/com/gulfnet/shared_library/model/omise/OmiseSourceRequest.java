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
public class OmiseSourceRequest {
    
    @JsonProperty("amount")
    private Long amount; // Amount in smallest currency unit (e.g., satang for THB)
    
    @JsonProperty("currency")
    private String currency; // Currency code (e.g., "THB")
    
    @JsonProperty("type")
    private String type; // Source type, typically same as method
    
    @JsonProperty("platform_type")
    private String platformType;
}
