package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantRowRepository extends JpaRepository<RestaurantRow, UUID> {

    @Query("SELECT r FROM RestaurantRow r "
            + "WHERE r.restaurantSection.id = :sectionId "
            + "AND r.isDeleted = false "
            + "ORDER BY r.rowOrder")
    List<RestaurantRow> findActiveByRestaurantSectionIdOrderByRowOrder(@Param("sectionId") UUID sectionId);
}
