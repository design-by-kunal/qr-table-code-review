package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line-level refund item.
 *
 * Stores which ordered item/combo was refunded, how many units, and for what amount.
 * This allows us to reconstruct the refund details without relying only on JSON.
 */
@Entity
@Table(name = "refund_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Parent refund this item belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Refund refund;

    /**
     * Ordered item being refunded (for single menu items).
     * Exactly one of orderedItem / orderedCombo will be set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_item_id")
    private OrderedItem orderedItem;

    /**
     * Ordered combo being refunded (for combos).
     * Exactly one of orderedItem / orderedCombo will be set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_combo_id")
    private OrderedCombo orderedCombo;

    /**
     * Quantity being refunded (can be partial, e.g. 1 of 3).
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Refund amount for this line.
     * Already calculated (including proportional logic for partial quantities).
     */
    @Column(name = "refund_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal refundAmount;
}


