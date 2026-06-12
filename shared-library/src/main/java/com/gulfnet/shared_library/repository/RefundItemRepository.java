package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, UUID> {
    
    /**
     * Find all refund items by refund ID
     */
    List<RefundItem> findByRefund_Id(UUID refundId);
}

