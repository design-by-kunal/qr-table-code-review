package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RequestTypeListResponse {
    private List<RequestTypeResponse> requestTypes;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

