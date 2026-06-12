package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TableShape;
import com.gulfnet.shared_library.enums.TableStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_table")
@Getter
@Setter
@ToString(exclude = {
        "restaurantRow",
        "createdBy",
        "updatedBy",
        "tableSectionRequestedBy",
        "tableSectionReviewedBy"
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_row_id", nullable = false)
    private RestaurantRow restaurantRow;

    @Column(name = "table_order")
    private Integer tableOrder;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "shape")
    private TableShape shape;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "table_code", nullable = false)
    private String tableCode;
        
    @Enumerated(EnumType.STRING)
    @Column(name = "table_status")
    private TableStatus tableStatus;

    private String qrCodeUrl;

    @Column(name = "print_qr_code_url")
    private String printQrCodeUrl;

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

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "is_virtual", nullable = false)
    @Builder.Default
    private Boolean isVirtual = false;

    @Column(name = "block_reason")
    private String blockReason;

    // Table/Section request fields (for approval workflow - Manager to HQ_ADMIN)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "table_section_request_status")
    private RequestStatus tableSectionRequestStatus = RequestStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_section_request_data", columnDefinition = "jsonb")
    private String tableSectionRequestData;

    @Column(name = "table_section_requested_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime tableSectionRequestedAt;

    @Column(name = "table_section_reviewed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime tableSectionReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_section_requested_by")
    private User tableSectionRequestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_section_reviewed_by")
    private User tableSectionReviewedBy;

    @Column(name = "table_section_request_comments", length = 500)
    private String tableSectionRequestComments;
}
