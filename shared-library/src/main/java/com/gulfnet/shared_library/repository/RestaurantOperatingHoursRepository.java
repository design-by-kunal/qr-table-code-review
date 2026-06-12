package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantOperatingHours;
import com.gulfnet.shared_library.enums.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantOperatingHoursRepository extends JpaRepository<RestaurantOperatingHours, UUID> {
    Optional<RestaurantOperatingHours> findByRestaurant_IdAndDayOfWeek(UUID restaurantId, DayOfWeek dayOfWeek);

    @Query("SELECT roh FROM RestaurantOperatingHours roh LEFT JOIN FETCH roh.slots "
            + "WHERE roh.restaurant.id = :restaurantId AND roh.dayOfWeek = :dayOfWeek")
    Optional<RestaurantOperatingHours> findByRestaurant_IdAndDayOfWeekWithSlots(
            @Param("restaurantId") UUID restaurantId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek);
    List<RestaurantOperatingHours> findByRestaurant_IdAndDayOfWeekIn(UUID restaurantId, List<DayOfWeek> days);
    List<RestaurantOperatingHours> findByRestaurant_Id(UUID restaurantId);

    // Batch method to get operating hours for multiple restaurants
    List<RestaurantOperatingHours> findByRestaurant_IdIn(List<UUID> restaurantIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM RestaurantOperatingHours roh WHERE roh.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") UUID restaurantId);
}
