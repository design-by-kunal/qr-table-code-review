package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.gulfnet.shared_library.enums.QrCodeType;

@Entity
@Table(name = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "issued_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime issuedAt;

    @Column(name = "expired_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiredAt;

    @Column(name = "token_expiry_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime tokenExpiryAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "qr_code_type")
    private QrCodeType qrCodeType = QrCodeType.STATIC;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

}
