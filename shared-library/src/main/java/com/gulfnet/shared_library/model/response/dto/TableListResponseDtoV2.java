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
public class TableListResponseDtoV2 {
    private TableResponseV2 virtualTable; // Virtual table details at the top
    private List<SectionResponseV2> sections;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

