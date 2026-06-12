package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logging")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogging {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;


    public enum AuditAction {
        CREATE_EMPLOYEE,
        UPDATE_EMPLOYEE,
        DELETE_EMPLOYEE,
        APPROVE_PROFILE_UPDATE,
        DECLINE_PROFILE_UPDATE,
        UNASSIGN_RESTAURANT,
        ASSIGN_RESTAURANT,
        CHANGE_EMPLOYEE_STATUS,
        BULK_DELETE_EMPLOYEES,
        BULK_UPLOAD_EMPLOYEES
    }
}
