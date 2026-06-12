package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateLayoutRequestDto {
    
    @NotNull
    private List<TemplateSectionDto> sections;
}
