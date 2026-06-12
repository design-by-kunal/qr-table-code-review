package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.EmailSchedule;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailScheduleRepository extends JpaRepository<EmailSchedule, UUID>, JpaSpecificationExecutor<EmailSchedule> {

    List<EmailSchedule> findByCreatedBy_IdAndIsActiveTrue(UUID createdById);

    /**
     * All schedules created by a user (e.g. to cancel when the user is deleted).
     */
    List<EmailSchedule> findByCreatedBy_Id(UUID createdById);

    /**
     * Schedules created by a user for a specific restaurant (e.g. to cancel when they leave that restaurant).
     */
    List<EmailSchedule> findByCreatedBy_IdAndRestaurant_Id(UUID createdById, UUID restaurantId);

    List<EmailSchedule> findByRestaurant_IdAndIsActiveTrue(UUID restaurantId);

    List<EmailSchedule> findByRestaurantGroup_IdAndIsActiveTrue(UUID restaurantGroupId);

    Optional<EmailSchedule> findByQuartzJobKey(String quartzJobKey);

    List<EmailSchedule> findAllByIsActiveTrue();

    /**
     * Finds all active email schedules with optional filtering by restaurant, restaurant group,
     * report type, frequency, and creator. All filters are optional (null values are ignored).
     * Supports pagination and sorting via Pageable.
     *
     * @param restaurantId     optional restaurant ID filter, null returns all restaurants
     * @param restaurantGroupId optional restaurant group ID filter, null returns all groups
     * @param reportType        optional report type filter, null returns all report types
     * @param frequency         optional schedule frequency filter, null returns all frequencies
     * @param createdById       optional creator ID filter, null returns all creators
     * @param pageable          pagination and sorting parameters
     * @return paginated list of active email schedules matching the optional filters
     */
    @Query("SELECT es FROM EmailSchedule es WHERE es.isActive = true " +
           "AND (es.restaurant.id = :restaurantId OR :restaurantId IS NULL) " +
           "AND (es.restaurantGroup.id = :restaurantGroupId OR :restaurantGroupId IS NULL) " +
           "AND (es.reportType = :reportType OR :reportType IS NULL) " +
           "AND (es.frequency = :frequency OR :frequency IS NULL) " +
           "AND (es.createdBy.id = :createdById OR :createdById IS NULL)")
    Page<EmailSchedule> findAllActiveSchedules(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("reportType") ReportType reportType,
            @Param("frequency") ScheduleFrequency frequency,
            @Param("createdById") UUID createdById,
            Pageable pageable);
}

