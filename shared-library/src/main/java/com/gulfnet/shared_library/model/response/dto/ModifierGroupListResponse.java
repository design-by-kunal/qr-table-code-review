package com.gulfnet.shared_library.model.response.dto;

import java.util.List;

import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupBasicDetailsResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModifierGroupListResponse {
    private List<ModifierGroupBasicDetailsResponse> modifierGroups;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
}
