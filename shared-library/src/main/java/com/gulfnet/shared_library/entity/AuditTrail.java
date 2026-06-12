package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "audit_trail")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "log_number", unique = true, nullable = false)
    private String logNumber; // e.g., "REQ15097"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // User who performed the action

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant; // Optional - not all actions are restaurant-specific

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Column(name = "notes", length = 1000)
    private String notes;

    // Additional metadata fields
    @Column(name = "entity_id")
    private UUID entityId; // ID of the related entity (order, transaction, etc.)

    @Column(name = "entity_type", length = 100)
    private String entityType; // Type of related entity (ORDER, TRANSACTION, etc.)

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "requested_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime requestedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (status == null) {
            // Default to NA for non-request actions
            // Request-type actions (REFUND, CANCELLATION, DISCOUNT) should explicitly set status
            status = RequestStatus.NA;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

