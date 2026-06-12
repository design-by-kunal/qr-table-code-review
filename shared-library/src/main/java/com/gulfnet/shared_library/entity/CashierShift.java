package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.ShiftStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "cashier_shift")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_drawer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CashDrawer cashDrawer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Shift shift; // Link to shift definition (Morning Shift, Evening Shift, etc.)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShiftStatus status;

    @Column(name = "opening_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 10, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "expected_closing_balance", precision = 10, scale = 2)
    private BigDecimal expectedClosingBalance;

    @Column(name = "discrepancy_amount", precision = 10, scale = 2)
    private BigDecimal discrepancyAmount;

    @Column(name = "discrepancy_reason", length = 500)
    private String discrepancyReason;

    @Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime startedAt;

    @Column(name = "closed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User approvedBy; // Manager who approved the shift

    @Column(name = "approved_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (startedAt == null) {
            startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

