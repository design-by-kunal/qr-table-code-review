package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String message;
    private String type;
    private Boolean read;

    @Column(name = "body_key")
    private String bodyKey;

    @Column(name = "body_args", columnDefinition = "TEXT")
    private String bodyArgs;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", columnDefinition = "UUID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", columnDefinition = "UUID")
    private User createdBy;
}
