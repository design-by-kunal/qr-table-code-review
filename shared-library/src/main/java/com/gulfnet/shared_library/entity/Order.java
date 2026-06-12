package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    /**
     * GMO PG LinkType Plus {@code transaction.OrderID}: max 27 half-width chars, unique per payment.
     */
    @Column(name = "gmo_link_order_id", length = 27)
    private String gmoLinkOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_table_id", nullable = false)
    private RestaurantTable restaurantTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    private User waiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "sub_total", precision = 10, scale = 2)
    private BigDecimal subTotal;

    @Column(name = "discount_code")
    private String discountCode;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    private DiscountType discountType;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "alcoholic_tax_amount", precision = 10, scale = 2)
    private BigDecimal alcoholicTaxAmount;

    @Column(name = "non_alcoholic_tax_amount", precision = 10, scale = 2)
    private BigDecimal nonAlcoholicTaxAmount;

    @Column(name = "alcoholic_taxable_amount", precision = 10, scale = 2)
    private BigDecimal alcoholicTaxableAmount;

    @Column(name = "non_alcoholic_taxable_amount", precision = 10, scale = 2)
    private BigDecimal nonAlcoholicTaxableAmount;

    @Column(name = "service_charge_amount", precision = 10, scale = 2)
    private BigDecimal serviceChargeAmount;

    @Column(name = "packing_charge_amount", precision = 10, scale = 2)
    private BigDecimal packingChargeAmount;

    @Column(name = "additional_discount_value", precision = 10, scale = 2)
    private BigDecimal additionalDiscountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "additional_discount_type")
    private DiscountType additionalDiscountType;

    @Column(name = "additional_discount_amount", precision = 10, scale = 2)
    private BigDecimal additionalDiscountAmount;

    @Column(name = "additional_discount_reason")
    private String additionalDiscountReason;

    // Additional discount request fields (for approval workflow)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "additional_discount_request_status")
    private RequestStatus additionalDiscountRequestStatus = RequestStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_discount_request_data", columnDefinition = "jsonb")
    private String additionalDiscountRequestData;

    @Column(name = "additional_discount_requested_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime additionalDiscountRequestedAt;

    @Column(name = "additional_discount_reviewed_at")
    private OffsetDateTime additionalDiscountReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "additional_discount_requested_by")
    private User additionalDiscountRequestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "additional_discount_reviewed_by")
    private User additionalDiscountReviewedBy;

    @Column(name = "additional_discount_request_comments", length = 500)
    private String additionalDiscountRequestComments;

    // Order cancellation request fields (for approval workflow)
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

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "email", nullable = true)
    private String email;

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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderedItem> orderedItems = new ArrayList<>();

    // NEW: Add combo relationship
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderedCombo> orderedCombos = new ArrayList<>();
}
