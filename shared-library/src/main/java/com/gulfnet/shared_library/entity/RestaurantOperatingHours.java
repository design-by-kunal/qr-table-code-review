package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.gulfnet.shared_library.enums.DayOfWeek;

@Entity
@Table(name = "restaurant_operating_hours")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantOperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek; // e.g., MONDAY, TUESDAY

    @OneToMany(mappedBy = "restaurantOperatingHours", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<OperatingHourSlot> slots;

    private Boolean isClosed;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdByUser;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedByUser;
}
