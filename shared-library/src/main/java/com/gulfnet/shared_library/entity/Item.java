package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "item_code", length = 64)
    private String itemCode;

    private Double basePrice;

    private String imageUrl;

	private String thumbnailUrl;

    private Boolean outOfStock;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Enumerated(EnumType.STRING)
    private DietaryPreference dietaryPreference;

    @Enumerated(EnumType.STRING)
    private ItemOrderType itemOrderType;

    @Enumerated(EnumType.STRING)
    private AlcoholType alcoholType;

    private Boolean isDeleted;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Builder.Default
    private Boolean hasModifierAssigned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedBy;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemTranslation> translations;
}