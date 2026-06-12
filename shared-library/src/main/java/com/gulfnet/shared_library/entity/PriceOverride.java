package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "price_override")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_level", nullable = false)
    private OverrideLevel overrideLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false)
    private OverrideType overrideType;

    @Column(name = "override_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal overrideValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PriceOverrideStatus status = PriceOverrideStatus.UNSCHEDULED;

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

    @OneToMany(mappedBy = "priceOverride", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PriceOverrideTranslation> translations = new ArrayList<>();

    @Column(name = "valid_from", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validTo;

    @Column(name = "start_time", columnDefinition = "TIMETZ")
    private OffsetTime startTime;

    @Column(name = "end_time", columnDefinition = "TIMETZ")
    private OffsetTime endTime;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

