package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Session;
import com.gulfnet.shared_library.enums.QrCodeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Session findByRestaurantIdAndTableIdAndExpiredAtIsNull(UUID restaurantId, UUID tableId);
    
    List<Session> findByTableIdAndExpiredAtIsNull(UUID tableId);

    /**
     * Batch fetch active sessions for multiple tables. Used for batch hasOngoingOrders check.
     */
    @Query("SELECT s FROM Session s WHERE s.tableId IN :tableIds AND s.expiredAt IS NULL")
    List<Session> findByTableIdInAndExpiredAtIsNull(@Param("tableIds") Collection<UUID> tableIds);
    
    Optional<Session> findById(UUID id);
    
    @Query("SELECT MAX(s.sequenceNo) FROM Session s WHERE s.tableId = :tableId AND s.expiredAt IS NULL")
    Integer findMaxSequenceNoByTableIdAndExpiredAtIsNull(@Param("tableId") UUID tableId);

    List<Session> findByTableIdAndQrCodeTypeAndExpiredAtIsNull(UUID tableId, QrCodeType qrCodeType);

    @Query("""
            SELECT s FROM Session s
            WHERE s.expiredAt IS NULL
              AND NOT EXISTS (SELECT 1 FROM Order o WHERE o.session.id = s.id)
            """)
    List<Session> findActiveSessionsWithoutOrders();

    @Query("""
            SELECT s FROM Session s
            WHERE s.restaurantId = :restaurantId
              AND s.expiredAt IS NULL
              AND NOT EXISTS (SELECT 1 FROM Order o WHERE o.session.id = s.id)
            """)
    List<Session> findActiveSessionsWithoutOrdersByRestaurantId(@Param("restaurantId") UUID restaurantId);

}
