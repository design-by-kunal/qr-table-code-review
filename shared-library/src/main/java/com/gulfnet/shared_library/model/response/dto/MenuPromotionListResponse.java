package com.gulfnet.shared_library.model.response.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuPromotionListResponse {
    private List<MenuPromotionResponseDto> promotions;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
}
