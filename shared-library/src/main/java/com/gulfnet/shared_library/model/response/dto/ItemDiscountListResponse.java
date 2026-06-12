package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;


import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.AllArgsConstructor;
import lombok.Builder;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDiscountListResponse {
    private UUID menuId;
    private List<DiscountResponse> discounts;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}
