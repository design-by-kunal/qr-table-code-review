package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "restaurant_group_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantGroupTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String name;

    @Column(name = "language_code", length = 5)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_group_id", columnDefinition = "UUID")
    private RestaurantGroup restaurantGroup;
}
