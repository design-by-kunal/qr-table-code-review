package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "charges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Charges {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", columnDefinition = "UUID")
    private Restaurant restaurant;

    private String name;

    @Column(name = "charge_type")
    private String chargeType; // e.g., 'percentage', 'fixed'

    private Double value;

    @Enumerated(EnumType.STRING)
    private OrderType applicableOn;

    @Column(name = "include_charge")
    private String includeCharge;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;

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
}
