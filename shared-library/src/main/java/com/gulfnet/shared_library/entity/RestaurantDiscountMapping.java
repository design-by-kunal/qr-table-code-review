package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;

import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.EntityStatus;

@Entity
@Table(name = "restaurant_discount_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDiscountMapping {

    @EmbeddedId
    @Builder.Default
    private RestaurantDiscountId id = new RestaurantDiscountId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("restaurantId")
    @JoinColumn(name = "restaurant_id", columnDefinition = "UUID")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("discountId")
    @JoinColumn(name = "discount_id", columnDefinition = "UUID")
    private Discount discount;

    @Column(name = "valid_from", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validTo;

    @Column(name = "start_time", columnDefinition = "TIMETZ")
    private OffsetTime startTime;

    @Column(name = "end_time", columnDefinition = "TIMETZ")
    private OffsetTime endTime;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    @Column(name = "days_of_week", columnDefinition = "text[]")
    private List<DayOfWeek> daysOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private EntityStatus status = EntityStatus.ACTIVE;

    @Column(name = "is_hide", nullable = false)
    @Builder.Default
    private Boolean isHide = false;
}

