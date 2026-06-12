package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedComboGroupResponse {
    private UUID comboGroupId;
    private String comboGroupName;
    private String comboGroupType;
    private Integer minSelect;
    private Integer maxSelect;
    private List<OrderedComboItemResponse> orderedItems;
}
