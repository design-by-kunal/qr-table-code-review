package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponseV2 {
    private String id;
    private String tableCode;
    private Integer tableOrder;
    private Integer capacity;
    private String tableStatus;
    private String blockReason;
    private Integer readyItems;
    private Integer pendingItems;
    private List<TableSessionInfo> sessions;
    private List<WaiterInfo> waiters;
}

