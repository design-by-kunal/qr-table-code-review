package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OnShiftStaffListResponse {
    private List<OnShiftStaff> onShiftStaff;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

