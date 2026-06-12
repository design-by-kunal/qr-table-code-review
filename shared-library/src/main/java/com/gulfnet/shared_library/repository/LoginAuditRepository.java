package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {

    Optional<LoginAudit> findByUser_Id(UUID userId);

    /**
     * Find all login audits for a list of user IDs
     * This helps avoid N+1 query problems when checking multiple users
     */
    @Query("SELECT la FROM LoginAudit la WHERE la.user.id IN :userIds")
    List<LoginAudit> findAllByUserIds(@Param("userIds") List<UUID> userIds);

    @Modifying
    @Transactional
    @Query("UPDATE LoginAudit la SET la.loginExpiryDate = :expiryDate WHERE la.user.id = :userId")
    void updateExpiryDateByUserId(
            @Param("userId") UUID userId,
            @Param("expiryDate") OffsetDateTime expiryDate
    );

    void deleteByUser_Id(UUID userId);
}
