package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierItemResponseDto {
    private UUID id;
    private UUID modifierGroupId;
    private String modifierCode;
    private String imageUrl;
    private BigDecimal price;
    private Integer sortOrder;
    private Boolean isDefault;
    private EntityStatus status;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<ModifierItemTranslationDto> translations;
}