package com.gulfnet.shared_library.model.response.dto;

import java.util.List;
import java.util.UUID;

import com.gulfnet.shared_library.enums.EntityStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDataResponse {
    private CategoryCreateResponse category;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
}