package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "email_schedule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_name", nullable = false, length = 255)
    private String scheduleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private ScheduleFrequency frequency;

    @Column(name = "scheduled_time", nullable = false, columnDefinition = "TIMETZ")
    @Builder.Default
    private OffsetTime scheduledTime = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC); // Always midnight UTC (00:00:00 UTC)

    @Column(name = "scheduled_day")
    private Integer scheduledDay; // Day of week (1-7) for WEEKLY, Day of month (1-31) for MONTHLY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_group_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RestaurantGroup restaurantGroup;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail; // Automatically set from creator's email

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User createdBy;

    /**
     * Audit field: Timestamp when the email schedule record was created (always in UTC)
     */
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User updatedBy;

    /**
     * Audit field: Timestamp when the email schedule record was last updated (always in UTC)
     */
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    /**
     * Operational field: Timestamp when the scheduled email job last executed successfully (always in UTC)
     * This is different from created_at as it tracks job execution, not record creation
     */
    @Column(name = "last_executed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastExecutedAt;

    /**
     * Operational field: Timestamp when the scheduled email job will execute next (always in UTC)
     * This is calculated based on frequency and scheduled_time, used by Quartz scheduler
     */
    @Column(name = "next_execution_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime nextExecutionAt;

    @Column(name = "quartz_job_key", length = 255)
    private String quartzJobKey; // Reference to Quartz job

    // Report generation parameters
    @Column(name = "period", length = 50)
    private String period; // e.g., "30_DAYS", "3_MONTHS", "6_MONTHS"

    /**
     * Business logic field: Start date for custom report period range (always in UTC)
     * This is different from created_at as it defines the report data range, not when the schedule was created
     */
    @Column(name = "start_date", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime startDate;

    /**
     * Business logic field: End date for custom report period range (always in UTC)
     * This is different from created_at as it defines the report data range, not when the schedule was created
     */
    @Column(name = "end_date", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime endDate;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        // Ensure start_date and end_date are normalized to UTC if provided
        normalizeDatesToUTC();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        // Ensure start_date and end_date are normalized to UTC if provided
        normalizeDatesToUTC();
    }

    /**
     * Normalizes start_date and end_date to UTC timezone.
     * Converts any OffsetDateTime values to UTC offset to ensure consistency.
     */
    private void normalizeDatesToUTC() {
        if (startDate != null && !startDate.getOffset().equals(ZoneOffset.UTC)) {
            startDate = startDate.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (endDate != null && !endDate.getOffset().equals(ZoneOffset.UTC)) {
            endDate = endDate.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (lastExecutedAt != null && !lastExecutedAt.getOffset().equals(ZoneOffset.UTC)) {
            lastExecutedAt = lastExecutedAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (nextExecutionAt != null && !nextExecutionAt.getOffset().equals(ZoneOffset.UTC)) {
            nextExecutionAt = nextExecutionAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (scheduledTime != null && !scheduledTime.getOffset().equals(ZoneOffset.UTC)) {
            scheduledTime = scheduledTime.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }
}

