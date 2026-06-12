package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetTime;
import java.util.UUID;

@Entity
@Table(name = "operating_hour_slot")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingHourSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_operating_hours_id")
    private RestaurantOperatingHours restaurantOperatingHours;

    private OffsetTime fromTime;
    private OffsetTime toTime;
} 