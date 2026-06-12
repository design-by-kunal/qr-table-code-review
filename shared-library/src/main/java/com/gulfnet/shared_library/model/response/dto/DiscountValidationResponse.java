package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountValidationResponse {
    private UUID discountId;
    private String discountCode;
    private DiscountType discountType;
    private BigDecimal originalSubTotal;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private BigDecimal orderValueThreshold;
}