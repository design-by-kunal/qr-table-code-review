package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "restaurant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_code", unique = true, columnDefinition = "VARCHAR(50)")
    private String restaurantCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_group_id", columnDefinition = "UUID")
    private RestaurantGroup restaurantGroup;

    private String city;
    private String area;
    private String state;
    private String address1;
    private String address2;
    private String latitude;
    private String longitude;
    private String locationPin;
    private String logoUrl;
    private String logoThumbnailUrl;
    private Boolean isDeleted;
    @Enumerated(EnumType.STRING)
    private QrCodeType tableQrCodeType;
    private String restaurantGroupName;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RestaurantTranslation> translations = new ArrayList<>();

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedBy;
    @Enumerated(EnumType.STRING)
    private EntityStatus status;
    private String paymentQrUrl;
    private String gstNumber;

    /** E.164 max 15 digits; column sized for formatted international input. */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;
    
    @Column(name = "kds_live_dashboard_reset_time", columnDefinition = "TIMETZ")
    private OffsetTime kdsLiveDashboardResetTime;
    
    @Column(name = "cashier_live_dashboard_reset_time", columnDefinition = "TIMETZ")
    private OffsetTime cashierLiveDashboardResetTime;
    
    // Alert Configuration Fields
    @Column(name = "sales_alert_threshold", precision = 10, scale = 2)
    private java.math.BigDecimal salesAlertThreshold;
    
    @Column(name = "refund_alert_percentage", precision = 5, scale = 2)
    private java.math.BigDecimal refundAlertPercentage;
    
    @Column(name = "cancellation_alert_percentage", precision = 5, scale = 2)
    private java.math.BigDecimal cancellationAlertPercentage;
    
    @Column(name = "alerts_enabled")
    private Boolean alertsEnabled;
    
    /**
     * Indicates whether this restaurant uses its own payment account
     * (e.g., its own Omise account/keys) instead of only system-level credentials.
     */
    @Column(name = "has_own_payment_account")
    private Boolean hasOwnPaymentAccount;
}
