package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "restaurant_section")
@Getter
@Setter
@ToString(exclude = {
        "restaurantLayout",
        "rows",
        "translations",
        "createdBy",
        "updatedBy",
        "tableSectionRequestedBy",
        "tableSectionReviewedBy"
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_layout_id", nullable = false)
    private RestaurantLayout restaurantLayout;

    @Column(name = "section_order")
    private Integer sectionOrder;

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

    @Builder.Default
    @OneToMany(mappedBy = "restaurantSection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantRow> rows = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "restaurantSection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantSectionTranslation> translations = new ArrayList<>();

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
