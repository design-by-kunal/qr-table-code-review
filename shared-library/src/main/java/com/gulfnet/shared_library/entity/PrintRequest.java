package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "print_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    private RequestStatus requestStatus = RequestStatus.OPEN;

    @Column(name = "file_url", nullable = true, length = 1000)
    private String fileUrl;

    @Column(name = "order_number", length = 255)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_table_id")
    private RestaurantTable restaurantTable;

    @Column(name = "refund_id")
    private UUID refundId;

    @Column(name = "requested_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime approvedAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime completedAt;

    @Column(name = "comments", length = 500)
    private String comments;

    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.requestedAt == null) {
            this.requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}


