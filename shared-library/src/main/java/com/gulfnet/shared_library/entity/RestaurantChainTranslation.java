package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "restaurant_chain_translation") 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantChainTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", columnDefinition = "VARCHAR(255)")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_chain_id", columnDefinition = "UUID")
    private RestaurantChain restaurantChain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", columnDefinition = "UUID")
    private Language language;
}
