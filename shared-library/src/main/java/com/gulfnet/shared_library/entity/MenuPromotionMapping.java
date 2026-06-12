package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "menu_promotion_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuPromotionMapping {
    @EmbeddedId
    private MenuPromotionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id", columnDefinition = "UUID")
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("promotionId")
    @JoinColumn(name = "promotion_id", columnDefinition = "UUID")
    private Promotion promotion;

    @Column(name = "valid_from", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validTo;
}
