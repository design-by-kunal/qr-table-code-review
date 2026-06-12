package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.gulfnet.shared_library.enums.TableShape;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTableDto {

    private UUID id; 
    
    @NotNull
    @Min(1)
    private Integer tableOrder;

    @NotNull
    private TableShape shape;

    @NotNull
    @Min(0)
    private Integer capacity;

    @NotBlank(message = "Table code is required and cannot be empty")
    private String tableCode;
}
