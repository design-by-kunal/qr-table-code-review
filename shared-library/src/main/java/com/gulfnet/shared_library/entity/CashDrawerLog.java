package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.DrawerEventType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "cash_drawer_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CashierShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drawer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CashDrawer drawer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user; // User who performed the action

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private DrawerEventType eventType;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount; // Positive for inflows, negative for outflows

    @Column(name = "expected_amount", precision = 10, scale = 2)
    private BigDecimal expectedAmount; // What should have been in the drawer

    @Column(name = "gross_in", precision = 10, scale = 2)
    private BigDecimal grossIn; // Physical cash put into drawer (e.g., tendered cash, change collected)

    @Column(name = "gross_out", precision = 10, scale = 2)
    private BigDecimal grossOut; // Physical cash taken out of drawer (e.g., change returned, refund offered)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Transaction transaction; // For SALE_INFLOW events

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Refund refund; // For SALE_REFUND events

    @Column(name = "reason", length = 500)
    private String reason; // For manual operations (deposit/withdrawal)

    @Column(name = "notes", length = 1000)
    private String notes; // Additional notes

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User createdBy; // User who created the log entry

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

