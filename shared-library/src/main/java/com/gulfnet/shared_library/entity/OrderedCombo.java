package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.ItemStatus;
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
@Table(name = "ordered_combo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedCombo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    // Base price per combo unit at order time
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    // Total price for this combo line (includes quantity); nullable for backward compatibility
    @Column(name = "total_combo_amount", precision = 10, scale = 2, nullable = true)
    private BigDecimal totalComboAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false)
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
    
    // One-to-Many relationship with ordered items that belong to this combo
    @OneToMany(mappedBy = "orderedCombo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderedItem> orderedItems = new ArrayList<>();

    // Cancellation request fields (similar to OrderedItem cancellation request pattern)
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

    /**
     * Indicates whether this combo line was included in the paid amount at the time a transaction
     * was completed. This flag is set once on payment completion and must NOT be modified by
     * later cancellations.
     */
    @Builder.Default
    @Column(name = "included_in_payment", columnDefinition = "boolean default false")
    private Boolean includedInPayment = false;
}
