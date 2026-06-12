package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftListResponse {
    private List<ShiftResponse> shifts;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
}
