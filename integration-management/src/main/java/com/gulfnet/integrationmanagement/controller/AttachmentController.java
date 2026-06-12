package com.gulfnet.integrationmanagement.controller;

import com.gulfnet.integrationmanagement.service.AttachmentService;
import com.gulfnet.shared_library.model.response.dto.AttachmentResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService attachmentService;

    @PostMapping("/upload")
    public ResponseDto<List<AttachmentResponse>> uploadFiles(
            HttpServletRequest request,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "action", required = false) String action) {
        return attachmentService.uploadFiles(request, files, action);
    }
}
