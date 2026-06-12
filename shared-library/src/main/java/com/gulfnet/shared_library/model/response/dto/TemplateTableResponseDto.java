package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.gulfnet.shared_library.enums.TableShape;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateTableResponseDto {

    private UUID id;

    private Integer tableOrder;

    private TableShape shape;

    private Integer capacity;

    private String tableCode;
}

