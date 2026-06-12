package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.UserPasswordAudit;
import com.gulfnet.shared_library.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPasswordAuditRepository extends JpaRepository<UserPasswordAudit, UUID> {
    
    List<UserPasswordAudit> findByUserIdAndStatusAndActionOrderByCreatedAtDesc(
            UUID userId, String status, String action);
    
    // Find the latest pending OTP for a user
    Optional<UserPasswordAudit> findTopByUserAndActionAndStatusOrderByCreatedAtDesc(
            User user, String action, String status);
    
    // Find expired OTPs
    List<UserPasswordAudit> findByActionAndStatusAndOtpExpiryDateBefore(
            String action, String status, OffsetDateTime expiryDate);
    
    // Find old completed OTPs for cleanup
    List<UserPasswordAudit> findByActionAndStatusAndCreatedAtBefore(
            String action, String status, OffsetDateTime createdAt);
} 