package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.TableShape;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_table")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_row_id", nullable = false)
    private TemplateRow templateRow;

    @Column(name = "table_order")
    private Integer tableOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape")
    private TableShape shape;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "table_code", nullable = false)
    private String tableCode;

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
}
