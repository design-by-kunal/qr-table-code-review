package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleTranslationDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailScheduleRequest {

    /**
     * Legacy field – no longer required from the client.
     * The actual stored schedule name is derived from translations.
     */
    private String scheduleName;

    @NotNull(message = "{email.schedule.report.type.required}")
    private ReportType reportType;

    @NotNull(message = "{email.schedule.frequency.required}")
    private ScheduleFrequency frequency;

    @NotNull(message = "{email.schedule.time.required}")
    private OffsetTime scheduledTime; // Time in UTC format (e.g., "08:20:00+00:00" or "08:20:00Z") - used for all frequencies (DAILY, WEEKLY, MONTHLY)

    private Integer scheduledDay; // Required for WEEKLY (1-7, where 1=Sunday, 2=Monday, ..., 7=Saturday). For MONTHLY, always uses day 1 (first day of month)

    private UUID restaurantId; // For Manager

    private UUID restaurantGroupId; // For HQ_ADMIN

    private String period; // e.g., "30_DAYS", "3_MONTHS", "6_MONTHS"

    private OffsetDateTime startDate;

    private OffsetDateTime endDate;

    /**
     * Translations for the schedule name (e.g., EN, JA, TH).
     * At least one translation with a non-blank name must be provided.
     */
    private List<EmailScheduleTranslationDto> translations;
}

