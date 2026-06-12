package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "template_section")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_layout_id", nullable = false)
    private TemplateLayout layoutTemplate;

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

    @OneToMany(mappedBy = "templateSection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TemplateRow> rows = new ArrayList<>();
    
    @OneToMany(mappedBy = "templateSection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TemplateSectionTranslation> translations = new ArrayList<>();
}
