package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.BulkUploadListResponse;
import com.gulfnet.shared_library.enums.UploadType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import com.gulfnet.shared_library.enums.BulkUploadStatus;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

public interface BulkUserUploadService {
    


    void processAndSaveUsersFromLocalFile(String localFilePath, byte[] imageZipFileBytes, String imageZipFileName, String userId, String language, 
                                         UUID bulkUploadId, String totalRecords);
    
    ResponseEntity<Void> downloadTemplate(HttpServletResponse response) throws IOException;
    
    List<String[]> readCsvFile(MultipartFile file, Charset charset);

    ResponseDto<BulkUploadListResponse> getBulkUploads(
        BulkUploadStatus status,
        int page,
        int size,
        String search,
        String sortBy,
        String sortDirection,
        UploadType uploadType); 
} 
