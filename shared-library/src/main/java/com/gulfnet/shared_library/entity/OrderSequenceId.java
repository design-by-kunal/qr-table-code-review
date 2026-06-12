package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSequenceId implements Serializable {

    private UUID restaurantId;
    private OrderType orderType;
    private LocalDate effectiveDate;
}
