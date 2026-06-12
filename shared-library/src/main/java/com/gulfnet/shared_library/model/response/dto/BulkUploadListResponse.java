package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.entity.BulkUpload;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadListResponse {
    private List<BulkUploadWithPresignedUrls> bulkUploads;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
} 