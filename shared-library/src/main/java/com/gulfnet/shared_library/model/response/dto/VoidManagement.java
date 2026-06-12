package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gulfnet.shared_library.serializer.BigDecimalSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoidManagement {
    @JsonSerialize(using = BigDecimalSerializer.class)
    private BigDecimal totalWastageAmount;
    
    private Long totalWastageItems;
}
