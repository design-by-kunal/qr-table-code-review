package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletedUserResponse {
    private UserResponseDto user;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}
