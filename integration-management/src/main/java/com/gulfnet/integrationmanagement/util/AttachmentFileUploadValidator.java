package com.gulfnet.integrationmanagement.util;

import com.gulfnet.integrationmanagement.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AttachmentFileUploadValidator {

    public static final String IMAGE = "images";
    public static final String VIDEO = "videos";
    public static final String AUDIO = "audios";
    public static final String DOCUMENT = "documents";

    private final AttachmentUploadFileConfig attachmentUploadFileConfig;
    private final TikaMimeTypeDetector tikaMimeTypeDetector;

    /**
     * Validates an uploaded file using defense-in-depth checks: extension allowlist,
     * Tika-detected MIME type, and per-category maximum file size.
     *
     * @param file the multipart file to validate
     * @return the Tika-detected MIME type for the file content
     * @throws ValidationException if the file fails extension, MIME, or size validation
     */
    public String validate(MultipartFile file) {
        String extension = getFileExtension(file);
        if (extension == null || extension.isBlank()) {
            throw new ValidationException("422", "File type validation error");
        }
        extension = extension.toLowerCase();

        if (!isAllowedFileType(extension)) {
            throw new ValidationException("422", "File type validation error");
        }

        String detectedMimeType;
        try {
            detectedMimeType = tikaMimeTypeDetector.detectMimeType(file);
        } catch (IOException e) {
            throw new ValidationException("422", "File type validation error");
        }

        if (!AllowedMimeTypes.isAllowed(extension, detectedMimeType)) {
            throw new ValidationException("422", "File type validation error");
        }

        String fileType = getFileType(extension);
        long maxFileSize = getMaxFileSize(fileType);
        if (file.getSize() > maxFileSize) {
            throw new ValidationException("422", "File size validation error");
        }

        return detectedMimeType;
    }

    private boolean isAllowedFileType(String extension) {
        return attachmentUploadFileConfig.getAllowedPhotosExt().contains(extension)
                || attachmentUploadFileConfig.getAllowedAudiosExt().contains(extension)
                || attachmentUploadFileConfig.getAllowedVideosExt().contains(extension)
                || attachmentUploadFileConfig.getAllowedDocsExt().contains(extension);
    }

    public String getFileType(String extension) {
        if (attachmentUploadFileConfig.getAllowedPhotosExt().contains(extension)) {
            return IMAGE;
        } else if (attachmentUploadFileConfig.getAllowedAudiosExt().contains(extension)) {
            return AUDIO;
        } else if (attachmentUploadFileConfig.getAllowedVideosExt().contains(extension)) {
            return VIDEO;
        } else {
            return DOCUMENT;
        }
    }

    private long getMaxFileSize(String fileType) {
        return switch (fileType) {
            case IMAGE -> Long.parseLong(attachmentUploadFileConfig.getAllowedPhotosSize());
            case AUDIO -> Long.parseLong(attachmentUploadFileConfig.getAllowedAudiosSize());
            case VIDEO -> Long.parseLong(attachmentUploadFileConfig.getAllowedVideosSize());
            default -> Long.parseLong(attachmentUploadFileConfig.getAllowedDocsSize());
        };
    }

    public String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null ? StringUtils.getFilenameExtension(originalFilename) : null;
    }
}
