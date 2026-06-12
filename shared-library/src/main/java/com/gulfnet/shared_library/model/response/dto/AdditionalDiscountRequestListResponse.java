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
public class AdditionalDiscountRequestListResponse {
    private List<AdditionalDiscountRequestResponse> additionalDiscountRequests;
    
    // Pagination fields
    private int count; // Number of items in current page
    private long total; // Total number of items across all pages
    
    private MetaData metaData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaData {
        private int page;
        private int size;
        private int totalPages;
        private long totalRecords;
    }
}

