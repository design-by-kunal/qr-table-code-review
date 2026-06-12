package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.util.List;
import java.util.UUID;

import com.gulfnet.shared_library.model.response.dto.MenuTranslationDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuRequest {
    @Positive
    private Double version;
    private String status; // Use MenuStatus enum values: DRAFT, PUBLISHED, etc.

    private List<MenuTranslationDto> translations;
}
