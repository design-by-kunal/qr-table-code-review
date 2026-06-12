package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import com.gulfnet.shared_library.enums.EntityStatus;

@Entity
@Table(name = "restaurant_promotion_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPromotionMapping {

    @EmbeddedId
    @Builder.Default
    private RestaurantPromotionId id = new RestaurantPromotionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("restaurantId")
    @JoinColumn(name = "restaurant_id", columnDefinition = "UUID")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("promotionId")
    @JoinColumn(name = "promotion_id", columnDefinition = "UUID")
    private Promotion promotion;

    @Column(name = "valid_from", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private EntityStatus status = EntityStatus.ACTIVE;
}
