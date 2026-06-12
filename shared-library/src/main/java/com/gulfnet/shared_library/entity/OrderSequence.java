package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.OrderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "order_sequence")
@IdClass(OrderSequenceId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSequence {

    @Id
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Id
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
