package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DayOfWeek;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.OffsetTime;
import java.util.List;

@Data
public class OperatingHourDto {
    private DayOfWeek dayOfWeek;
    private List<Slot> slots;
    private Boolean isClosed;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Slot {
        private OffsetTime fromTime;
        private OffsetTime toTime;
    }
} 