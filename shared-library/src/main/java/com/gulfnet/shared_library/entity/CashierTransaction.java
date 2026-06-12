package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cashier_transaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_user_id")
    private User cashierUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    private String dayOfWeek;

    private Double openingBalance;
    private Double closingBalance;
    private Double totalCashSales;
    private Double totalCashRefunds;
    private Double expectedClosingBalance;
    private Double discrepancyAmount;
    private String discrepancyReason;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;
}
