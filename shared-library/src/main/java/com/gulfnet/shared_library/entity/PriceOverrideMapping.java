package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Entity
@Builder
@Table(
    name = "price_override_mapping"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Check(constraints = "(menu_id IS NOT NULL AND menu_category_mapping_id IS NULL) OR (menu_id IS NULL AND menu_category_mapping_id IS NOT NULL)")
public class PriceOverrideMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_override_id", nullable = false)
    private PriceOverride priceOverride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "menu_category_mapping_id")
    private UUID menuCategoryMappingId;
}


