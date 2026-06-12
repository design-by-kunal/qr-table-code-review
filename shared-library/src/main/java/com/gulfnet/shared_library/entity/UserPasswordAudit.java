package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_password_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    private Long otp;
    @Column(name = "otp_expiry_date", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime otpExpiryDate;
    private String status; // PENDING, COMPLETED, EXPIRED
    private String action; // CHANGE_PASSWORD, RESET_PASSWORD
    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
} 