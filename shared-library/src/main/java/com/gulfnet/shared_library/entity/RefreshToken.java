package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiresAt;


    @Column(name = "revoked", nullable = false)
    private Boolean revoked;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
} 