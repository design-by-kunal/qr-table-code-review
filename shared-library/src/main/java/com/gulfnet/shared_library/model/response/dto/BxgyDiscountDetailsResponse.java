package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BxgyDiscountDetailsResponse {
    private UUID discountId;
    private String discountName;
    private Integer buyQuantity;
    private Integer getQuantity;
    private List<BxgyItemDto> buyItems;
    private List<BxgyItemDto> getItems;
}
