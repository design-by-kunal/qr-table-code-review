package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerLogListResponse {
    private List<CashDrawerLogResponse> logs;
    private PaginationMetaData pagination;
}

