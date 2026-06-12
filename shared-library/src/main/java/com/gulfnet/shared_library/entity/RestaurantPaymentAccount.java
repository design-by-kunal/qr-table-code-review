package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "restaurant_payment_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPaymentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, columnDefinition = "UUID")
    private Restaurant restaurant;

    @Column(name = "payment_type", nullable = false, length = 50)
    private String paymentType;

    @Column(name = "encrypted_public_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPublicKey;

    @Column(name = "encrypted_secret_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecretKey;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (isDeleted == null) {
            isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

