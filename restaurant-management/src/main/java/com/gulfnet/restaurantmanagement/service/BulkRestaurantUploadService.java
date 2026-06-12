package com.gulfnet.restaurantmanagement.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.gulfnet.shared_library.model.response.dto.BulkUploadWithPresignedUrls;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import jakarta.servlet.http.HttpServletResponse;

public interface BulkRestaurantUploadService {

    ResponseEntity<Void> downloadRestaurantTemplate(HttpServletResponse response) throws IOException;

    ResponseDto<BulkUploadWithPresignedUrls> processRestaurantBulkUpload(MultipartFile file, MultipartFile imageZipFile, String userId) throws IOException;

    void processRestaurantsAsync(List<String[]> records, String userId, String language, UUID bulkUploadId);

}
