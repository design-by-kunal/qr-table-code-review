package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RefundType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "refund")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Transaction transaction;

    @Column(name = "refund_number", unique = true, length = 50)
    private String refundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", length = 20)
    private RefundType refundType;

    @Column(name = "total_refund_amount", precision = 10, scale = 2)
    private BigDecimal totalRefundAmount;

    @Column(name = "subtotal_refund_amount", precision = 10, scale = 2)
    private BigDecimal subtotalRefundAmount;

    @Column(name = "tax_refund_amount", precision = 10, scale = 2)
    private BigDecimal taxRefundAmount;

    @Column(name = "alcoholic_tax_refund_amount", precision = 10, scale = 2)
    private BigDecimal alcoholicTaxRefundAmount;

    @Column(name = "non_alcoholic_tax_refund_amount", precision = 10, scale = 2)
    private BigDecimal nonAlcoholicTaxRefundAmount;

    @Column(name = "alcoholic_taxable_refund_amount", precision = 10, scale = 2)
    private BigDecimal alcoholicTaxableRefundAmount;

    @Column(name = "non_alcoholic_taxable_refund_amount", precision = 10, scale = 2)
    private BigDecimal nonAlcoholicTaxableRefundAmount;

    @Column(name = "service_charge_refund_amount", precision = 10, scale = 2)
    private BigDecimal serviceChargeRefundAmount;

    @Column(name = "packing_charge_refund_amount", precision = 10, scale = 2)
    private BigDecimal packingChargeRefundAmount;

    @Column(name = "discount_refund_amount", precision = 10, scale = 2)
    private BigDecimal discountRefundAmount;

    @Column(name = "additional_discount_refund_amount", precision = 10, scale = 2)
    private BigDecimal additionalDiscountRefundAmount;

    @Column(name = "refund_method", length = 50)
    private String refundMethod;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @OneToMany(mappedBy = "refund", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<RefundItem> refundItems;

    // ==================== REFUND COMPLETION FIELDS ====================
    
    /**
     * Cash amount actually given to customer (refund offered)
     */
    @Column(name = "refund_offered", precision = 10, scale = 2)
    private BigDecimal refundOffered;

    /**
     * Change collected from customer (if refund offered > refund amount)
     */
    @Column(name = "change_collected", precision = 10, scale = 2)
    private BigDecimal changeCollected;

    /**
     * Timestamp when refund was completed
     */
    @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime completedAt;

    /**
     * User who completed the refund (cashier/manager)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    /**
     * S3 URL of the refund receipt PDF
     */
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    /**
     * Gateway-specific refund identifier for GMO refunds (sent as refund_id to GMO).
     */
    @Column(name = "gmo_refund_id", length = 50)
    private String gmoRefundId;

    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}

