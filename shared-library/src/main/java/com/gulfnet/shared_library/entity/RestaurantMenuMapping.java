package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Builder
@Table(name = "restaurant_menu_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantMenuMapping {
    
    @EmbeddedId
    private RestaurantMenuId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("restaurantId")
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RestaurantMenuMappingStatus status;
    
    @Column(name = "scheduled_publish_time", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime scheduledPublishTime;
} 