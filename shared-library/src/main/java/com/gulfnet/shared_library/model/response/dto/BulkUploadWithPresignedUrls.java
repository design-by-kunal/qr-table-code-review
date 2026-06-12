package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkUploadWithPresignedUrls {
    private UUID id;
    private String originalFilePresignedUrl;
    private String originalFileName;
    private String errorFilePresignedUrl;
    private String errorFileName;
    private Integer totalRecordCount;
    private Integer successRecordCount;
    private Integer failureRecordCount;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private UUID createdBy;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
} 