package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "discount")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "discount_code", length = 150)
    private String discountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    private DiscountType discountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_to")
    private AppliedTo appliedTo;

    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "order_value_threshold", precision = 10, scale = 2)
    private BigDecimal orderValueThreshold;

    @Column(name = "max_discount_value", precision = 10, scale = 2)
    private BigDecimal maxDiscountValue;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "current_usage")
    @Builder.Default
    private Integer currentUsage = 0;


    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EntityStatus status;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @OneToMany(mappedBy = "discount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiscountTranslation> translations = new ArrayList<>();


    @OneToMany(mappedBy = "discount", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiscountBxgyItem> bxgyItems = new ArrayList<>();
     

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
} 