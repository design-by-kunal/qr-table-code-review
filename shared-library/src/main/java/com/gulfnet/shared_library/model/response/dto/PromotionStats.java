package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PromotionStats {
    private Long activePromotionsCount;
    private Long upcomingPromotionsCount;
    private List<PromotionDetail> activePromotions;
    private List<PromotionDetail> upcomingPromotions;
}

