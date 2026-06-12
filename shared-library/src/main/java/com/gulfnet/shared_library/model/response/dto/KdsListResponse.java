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
public class KdsListResponse {
    private List<KdsResponse> kds;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}
