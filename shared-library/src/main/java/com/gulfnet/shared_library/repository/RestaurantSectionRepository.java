package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantSection;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RestaurantSectionRepository extends JpaRepository<RestaurantSection, UUID> {
    // Find sections with table/section requests
    Page<RestaurantSection> findByTableSectionRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find sections by table/section request status, with optional status filter.
     * If status is null, returns all sections with request status != NONE.
     */
    @Query("SELECT s FROM RestaurantSection s WHERE " +
           "(:status IS NULL AND s.tableSectionRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND s.tableSectionRequestStatus = :status)")
    Page<RestaurantSection> findByTableSectionRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);
}

