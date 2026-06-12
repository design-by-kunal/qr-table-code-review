package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerListResponse {
    private List<CashDrawerResponse> cashDrawers;
    private Long count;
    private Long total;
    private PaginationMetaData pagination;
}

