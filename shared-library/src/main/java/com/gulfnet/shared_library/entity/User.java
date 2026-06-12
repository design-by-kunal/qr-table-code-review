package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(exclude = {"createdBy", "updatedBy"})
@EqualsAndHashCode(exclude = {"createdBy", "updatedBy"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String contactNumber;
    private String photoUrl;
    private String photoThumbnailUrl;

    @Column(unique = true, nullable = false)
    private String userCode;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode; //'en' 2 letter, 'en-us' 5 letter

    @Column(name = "restaurant_id")
    private UUID restaurantId;
    @Builder.Default
    @Column(nullable = false)
    private Boolean isStatusLocked = false;
    @Builder.Default
    @Column(nullable = false)
    private Boolean isDeleted = false;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private RequestStatus profileUpdateRequestStatus = RequestStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_update_request_data", columnDefinition = "jsonb")
    private String profileUpdateRequestData;

    @Column(name = "profile_update_requested_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime profileUpdateRequestedAt;

    @Column(name = "profile_update_reviewed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime profileUpdateReviewedAt;

    @Column(name = "profile_update_request_locked_status")
    private Boolean profileUpdateRequestLockedStatus;

    @Column(name = "device_token")
    private String deviceToken;    

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type")
    private com.gulfnet.shared_library.enums.DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type")
    private com.gulfnet.shared_library.enums.AppType appType;

    @Column(name = "deleted_reason", length = 1000)
    private String deletedReason;
}
