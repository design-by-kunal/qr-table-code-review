package com.gulfnet.shared_library.model.response.dto;

import lombok.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableListResponseDto {
    
    private List<TableListResponse> tables;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;

}
