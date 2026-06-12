package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PeakHourAnalysis {
    // Hour range in format "9 AM - 10 AM"
    private String hourRange;
    
    // Percentage of total orders for this hour
    private BigDecimal percentage;
}

