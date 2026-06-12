package com.gulfnet.shared_library.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gulfnet.shared_library.entity.OrderSequence;
import com.gulfnet.shared_library.entity.OrderSequenceId;

@Repository
public interface OrderSequenceRepository extends JpaRepository<OrderSequence, OrderSequenceId> {
    // JPA repository for OrderSequence entity
    // Sequence generation is handled by OrderSequenceService in restaurant-management module
}
