package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedComboResponse {
    private UUID id;
    private UUID comboId;
    private String comboName;
    private String comboImageUrl;
    private ComboType comboType;
    private Integer quantity;
    private Boolean includedInPayment;
    private BigDecimal price;
    private BigDecimal totalComboAmount;
    private ItemStatus itemStatus;
    private String notes;
    private String reason;
    private RequestStatus requestStatus;
    private Boolean isAvailable; // availability flag for the combo
    private List<OrderedComboGroupResponse> comboGroups;
}
