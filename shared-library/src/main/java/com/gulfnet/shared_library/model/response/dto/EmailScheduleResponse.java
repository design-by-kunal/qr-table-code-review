package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailScheduleResponse {
    private UUID id;
    private ReportType reportType;
    private ScheduleFrequency frequency;
    private OffsetTime scheduledTime;
    private Integer scheduledDay;
    private UUID restaurantId;
    private String restaurantName;
    private UUID restaurantGroupId;
    private String restaurantGroupName;
    private String recipientEmail;
    private Boolean isActive;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt; // Audit field - keep as LocalDateTime
    private UUID updatedById;
    private String updatedByName;
    private LocalDateTime updatedAt; // Audit field - keep as LocalDateTime
    private OffsetDateTime lastExecutedAt;
    private OffsetDateTime nextExecutionAt;
    private String quartzJobKey;
    private String period;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;

    /**
     * Translations for the schedule name (e.g., EN, JA, TH).
     */
    private List<EmailScheduleTranslationDto> translations;
}

