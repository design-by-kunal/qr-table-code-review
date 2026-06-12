package com.gulfnet.shared_library.model.response.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableListResponse {

    private String id;
    private String tableCode;
    private Integer tableOrder;
    private String sectionId;
    private String sectionName;
    private Integer capacity;
    private String orderStatus;
    private String tableStatus;
    /** Manager/waiter block note when {@link #tableStatus} is BLOCKED; otherwise typically null. */
    private String blockReason;
    private Integer readyItems;
    private Integer pendingItems;
    private Integer rowOrder;
    private String rowId;
    private LocalDateTime occupiedAt;
    private List<TableSessionInfo> sessions;
    private List<WaiterInfo> waiters;

}
