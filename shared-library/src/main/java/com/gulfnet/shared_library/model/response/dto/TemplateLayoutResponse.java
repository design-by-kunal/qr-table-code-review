package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TemplateLayoutResponse {
    private UUID id;
    private EntityStatus status;
    private List<TemplateLayoutTranslationDto> translations;
}
