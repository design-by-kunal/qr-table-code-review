package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SalesStats {
    private String period; // "DAILY", "WEEKLY", "MONTHLY"
    private List<SalesDataPoint> dataPoints;
}

