package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRowDto {

    private UUID id; 
    
    @NotNull
    @Min(1)
    private Integer rowOrder;

    @NotNull
    private List<TemplateTableDto> tables;
}
