package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadListWithPresignedUrlsResponse {
    private List<BulkUploadWithPresignedUrls> bulkUploads;
    private Long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;
} 