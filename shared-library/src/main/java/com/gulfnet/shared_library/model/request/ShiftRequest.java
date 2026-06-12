package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.response.dto.ShiftTranslationDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRequest {

    @NotNull(message = "Translations are required")
    @Size(min = 1, message = "At least one translation is required")
    private List<ShiftTranslationDto> translations;

    @NotNull
    private OffsetTime startTime;

    @NotNull
    private OffsetTime endTime;

    @NotNull
    private EntityStatus status;
}
