package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.BxgyRole;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.AlcoholType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ordered_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    // Discounted unit price at the time of order placement
    @Column(name = "discounted_price", precision = 10, scale = 2, nullable = true)
    private BigDecimal discountedPrice;

    // Gross line total before discount: (base unit price + per-unit modifiers) * quantity
    @Column(name = "total_item_amount", precision = 10, scale = 2, nullable = true)
    private BigDecimal totalItemAmount;

    // Net line total after discount: (discounted unit price * quantity) + total modifiers
    @Column(name = "total_discounted_item_amount", precision = 10, scale = 2, nullable = true)
    private BigDecimal totalDiscountedItemAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "alcohol_type")
    private AlcoholType alcoholType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status")
    private ItemStatus itemStatus;

    // Status at the time of cancellation, used for wastage reporting
    @Enumerated(EnumType.STRING)
    @Column(name = "wastage_source_status")
    private ItemStatus wastageSourceStatus;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "reason", length = 500)
    private String reason;

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

    @OneToMany(mappedBy = "orderedItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderedItemModifier> orderedItemModifiers;

    // NEW: Add combo relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_combo_id")
    private OrderedCombo orderedCombo; // NULL for regular items, NOT NULL for combo items

    // Cancellation request fields (similar to User profile update request pattern)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_request_status")
    private RequestStatus cancellationRequestStatus = RequestStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_request_data", columnDefinition = "jsonb")
    private String cancellationRequestData;

    @Column(name = "cancellation_requested_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime cancellationRequestedAt;

    @Column(name = "cancellation_reviewed_at")
    private OffsetDateTime cancellationReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_requested_by")
    private User cancellationRequestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_reviewed_by")
    private User cancellationReviewedBy;

    @Column(name = "cancellation_comments", length = 500)
    private String cancellationComments;

    // BXGY discount tracking fields
    @Enumerated(EnumType.STRING)
    @Column(name = "bxgy_role")
    private BxgyRole bxgyRole;

    @Column(name = "discount_application_id")
    private UUID discountApplicationId;

    @Column(name = "discount_id")
    private UUID discountId;

    @Column(name = "free_quantity")
    private Integer freeQuantity;

    /**
     * Indicates whether this item line was included in the paid amount at the time a transaction
     * was completed. This flag is set once on payment completion and must NOT be modified by
     * later cancellations.
     */
    @Builder.Default
    @Column(name = "included_in_payment", columnDefinition = "boolean default false")
    private Boolean includedInPayment = false;
}
