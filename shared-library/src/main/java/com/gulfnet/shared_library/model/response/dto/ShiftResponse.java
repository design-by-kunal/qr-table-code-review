package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.List;
import java.util.UUID;
import java.time.OffsetTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
    private UUID id;

    private String shiftName; // Computed from translations for backward compatibility

    private List<ShiftTranslationDto> translations;

    private OffsetTime startTime;

    private OffsetTime endTime;

    private EntityStatus status;
}  