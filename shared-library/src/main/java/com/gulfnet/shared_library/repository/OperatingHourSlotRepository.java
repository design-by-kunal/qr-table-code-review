package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.OperatingHourSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Repository
public interface OperatingHourSlotRepository extends JpaRepository<OperatingHourSlot, UUID> {
    List<OperatingHourSlot> findByRestaurantOperatingHours_Id(UUID restaurantOperatingHoursId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM OperatingHourSlot ohs WHERE ohs.restaurantOperatingHours.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") UUID restaurantId);
} 