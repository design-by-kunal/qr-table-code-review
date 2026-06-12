package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TemplateLayoutStructureDto<T> {
    private T templateLayoutStructure;
}
