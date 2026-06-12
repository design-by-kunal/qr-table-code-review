package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDataResponse {
    private UserResponseDto user;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}
