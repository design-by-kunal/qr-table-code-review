package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserAccountDataResponse {
    private UserAccountDetailsDto user;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
} 