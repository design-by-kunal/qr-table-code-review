package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface BulkItemUploadService {
    ResponseEntity<Void> downloadTemplate(HttpServletResponse response, String locale) throws IOException;
    
    ResponseDto<BulkUpload> processBulkUpload(MultipartFile file, MultipartFile imageZipFile, String action, 
            String utfType, String language, String userId, String userRole, 
            String localeHeader) throws IOException;
    
    void processItemsAsync(List<String[]> records, String userId, String language, UUID bulkUploadId);
}