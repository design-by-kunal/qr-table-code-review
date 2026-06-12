package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PromotionDetail {
    private String promotionName;
    private String description;
    private String status;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String menuName;
}

