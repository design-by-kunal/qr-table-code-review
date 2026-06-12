package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountListData {
    private UUID id;
    private String discountCode;
    private DiscountType discountType;
    private AppliedTo appliedTo;
    private BigDecimal value;
    private BigDecimal orderValueThreshold;
    private BigDecimal maxDiscountValue;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Integer maxUses;
    private Integer currentUsage;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private List<DayOfWeek> daysOfWeek;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private UUID purchasedItemId;
    private UUID freeItemId;
    private EntityStatus status;
    private Boolean isHide;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<DiscountTranslationResponse> translations;
    @Builder.Default
    private Integer menuAssignedCount = 0;
    @Builder.Default
    private Boolean assignedToPromotion = false;
} 