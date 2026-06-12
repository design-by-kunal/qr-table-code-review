package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateSectionResponseDto {

    private UUID id;

    private Integer sectionOrder;

    private List<TemplateSectionTranslationResponseDto> translations;

    private List<TemplateRowResponseDto> rows;
}

