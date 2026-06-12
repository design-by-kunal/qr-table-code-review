package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Builder
@Table(name = "restaurant_group_menu_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantGroupMenuMapping {
    
    @EmbeddedId
    private RestaurantGroupMenuId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("restaurantGroupId")
    @JoinColumn(name = "restaurant_group_id")
    private RestaurantGroup restaurantGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id")
    private Menu menu;
    

}