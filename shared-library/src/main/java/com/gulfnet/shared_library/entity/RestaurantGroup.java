package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurant_group")
public class RestaurantGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_group_code", unique = true, columnDefinition = "VARCHAR(50)")
    private String restaurantGroupCode;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedBy;

    @OneToMany(mappedBy = "restaurantGroup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RestaurantGroupTranslation> translations = new ArrayList<>();

    // Alert Configuration Fields (Group-level defaults for restaurants in this group)
    @Column(name = "sales_alert_threshold", precision = 10, scale = 2)
    private java.math.BigDecimal salesAlertThreshold;

    @Column(name = "refund_alert_percentage", precision = 5, scale = 2)
    private java.math.BigDecimal refundAlertPercentage;

    @Column(name = "cancellation_alert_percentage", precision = 5, scale = 2)
    private java.math.BigDecimal cancellationAlertPercentage;

    @Column(name = "alerts_enabled")
    private Boolean alertsEnabled;
}
