package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ManagerListResponse {
    private List<ManagerDetails> managers;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}


