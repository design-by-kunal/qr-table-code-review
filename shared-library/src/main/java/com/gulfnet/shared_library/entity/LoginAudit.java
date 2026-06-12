package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.gulfnet.shared_library.enums.AppType;

@Entity
@Table(name = "login_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String ipAddress;
    private String userAgent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "app_type")
    private AppType appType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "login_expiry_date", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime loginExpiryDate;

    @Column(name = "last_seen_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastSeenAt;

    private String createdBy;

    @Column(name = "date_created", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime dateCreated;
 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", columnDefinition = "UUID")
    private User user;
}
