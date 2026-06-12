package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_discount_usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDiscountUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_item_id")
    private OrderedItem orderedItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id", nullable = false)
    private Discount discount;

    /**
     * Cached discount code for reporting convenience.
     */
    @Column(name = "discount_code", nullable = false)
    private String discountCode;

    /**
     * Logical discount category used in reports:
     * "Order", "Additional Discount", "Item", "Category", etc.
     */
    @Column(name = "discount_type", nullable = false)
    private String discountType;

    /**
     * Target level for the discount: ORDER / ITEM / CATEGORY.
     */
    @Column(name = "applied_to", nullable = false)
    private String appliedTo;

    /**
     * Actual money discounted by this discount on this order or item.
     */
    @Column(name = "discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

