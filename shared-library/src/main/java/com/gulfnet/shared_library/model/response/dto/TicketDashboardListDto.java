package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDashboardListDto {
    private List<TicketDashboardResponse> tickets;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
    
    // Summary counts by status
    private StatusCounts statusCounts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCounts {
        private Long pushed;
        private Long cooking;
        private Long delayed;
        private Long ready;
        private Long served;
        private Long canceled;
    }
}
