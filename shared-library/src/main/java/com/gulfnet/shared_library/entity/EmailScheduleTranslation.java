package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "email_schedule_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailScheduleTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_schedule_id", columnDefinition = "UUID", nullable = false)
    private EmailSchedule emailSchedule;
}

