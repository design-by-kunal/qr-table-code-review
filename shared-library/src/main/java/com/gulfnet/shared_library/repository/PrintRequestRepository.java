package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PrintRequest;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PrintRequestRepository extends JpaRepository<PrintRequest, UUID> {

    Page<PrintRequest> findByRestaurant_IdAndRequestStatus(UUID restaurantId, RequestStatus status, Pageable pageable);

    Page<PrintRequest> findByRequestedBy_Id(UUID requestedById, Pageable pageable);
}


