package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedRecord {
    private BulkUserUploadRequest userRequest;
    private String errorMessage;
} 