package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailListResponse {
    private List<AuditTrailResponse> logs;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
    private Long pendingCount;
    private Long approvedCount;
    private Long rejectedCount;
    private Long closedCount;
}

