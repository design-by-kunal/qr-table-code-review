package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.enums.MenuStatus;

@Entity
@Table(name = "menu")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_structure_id")
    private MenuStructure menuStructure;

    @Column(name = "menu_master_id")
    private UUID menuMasterId;

    private Double version;

    private Boolean isPublished;

    @Enumerated(EnumType.STRING)
    private MenuStatus status;

    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedBy;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuTranslation> translations;
}
