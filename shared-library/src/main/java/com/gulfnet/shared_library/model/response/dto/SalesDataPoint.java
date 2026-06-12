package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SalesDataPoint {
    private LocalDate date; // Date for daily, start date for weekly/monthly
    private Long orderCount;
    private BigDecimal totalSales;
}

