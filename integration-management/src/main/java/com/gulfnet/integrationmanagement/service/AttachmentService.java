package com.gulfnet.integrationmanagement.service;

import com.gulfnet.shared_library.enums.FileUploadAction;
import com.gulfnet.shared_library.model.response.dto.AttachmentResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.integrationmanagement.util.AttachmentFileUploadValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.exception.FileUploadException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentService {

    private final AWSService awsService;
    private final AttachmentFileUploadValidator attachmentFileUploadValidator;

    public static final String IMAGE = "images";
    public static final String VIDEO = "videos";
    public static final String AUDIO = "audios";
    public static final String DOCUMENT = "documents";

    /**
     * Handles upload of one or more files to S3 after validating each file and
     * resolving the correct upload folder based on the provided action.
     *
     * @param request the current HTTP servlet request (not directly used but available for extensions)
     * @param files   list of multipart files to upload
     * @param action  optional file upload action used to determine upload folder
     * @return {@link ResponseDto} containing a list of {@link AttachmentResponse} for uploaded files
     */
    public ResponseDto<List<AttachmentResponse>> uploadFiles(HttpServletRequest request, List<MultipartFile> files,
                                                           String action) {
        List<String> detectedMimeTypes = new ArrayList<>();
        for (MultipartFile file : files) {
            detectedMimeTypes.add(attachmentFileUploadValidator.validate(file));
        }
        FileUploadAction fileUploadAction = null;
        if(action != null)
            fileUploadAction = FileUploadAction.fromValue(action);
        return uploadFileToS3(files, fileUploadAction, detectedMimeTypes);
    }

    /**
     * Uploads multiple files to Amazon S3 storage.
     * Processes each file by determining its type, generating a unique filename,
     * uploading to the appropriate S3 folder based on the file type and action,
     * and creating pre-signed URLs for access.
     *
     * @param files  list of multipart files to upload to S3
     * @param action optional file upload action that determines the S3 folder structure
     * @return {@link ResponseDto} containing a list of {@link AttachmentResponse} objects
     *         with file details including filename, type, location, and pre-signed URL
     * @throws FileUploadException if an I/O error occurs during file processing or upload
     */
    private ResponseDto<List<AttachmentResponse>> uploadFileToS3(List<MultipartFile> files, FileUploadAction action,
                                                               List<String> detectedMimeTypes) {
        List<AttachmentResponse> attachmentResponses = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String detectedMimeType = detectedMimeTypes.get(i);
            try (InputStream inputStream = file.getInputStream()) {
                String extension = attachmentFileUploadValidator.getFileExtension(file);
                String attachmentType = attachmentFileUploadValidator.getFileType(extension.toLowerCase());
                String fileFolder;
                if(action != null){
                    fileFolder = getFileFolder(attachmentType, action);
                } else {
                    fileFolder = getFileFolder(attachmentType, null);
                }
                String filename = generateFileName(file, attachmentType);

                String key = fileFolder.substring(1) + "/" + filename;

                String uploadedFileUrl = awsService.uploadFile(inputStream, key, file.getSize(), detectedMimeType);
                String preSignedUrl = awsService.getPreSignedUrl(uploadedFileUrl);

                AttachmentResponse response = AttachmentResponse.builder().fileName(filename).fileType(detectedMimeType).fileLocation(awsService.getFullUrl(uploadedFileUrl)).attachmentType(attachmentType).build();
                response.setPreSignedUrl(preSignedUrl);
                attachmentResponses.add(response);
            } catch (IOException e) {
                log.error("Error handling file input stream: {}", e.getMessage());
                throw new FileUploadException("Failed to upload file to S3", e);
            }
        }
        return ResponseDto.<List<AttachmentResponse>>builder().data(attachmentResponses).message("Files uploaded successfully").build();
    }

    /**
     * Determines the S3 folder path where a file should be stored based on its
     * attachment type and an optional {@link FileUploadAction}.
     *
     * @param attachmentType the resolved attachment type (image, audio, video, document)
     * @param action         optional upload action to further specialize the folder path
     * @return a folder path string starting with "/"
     */
    private String getFileFolder(String attachmentType, FileUploadAction action) {
        String fileFolder = "/attachment/";

        if(action != null){
            // Handle bulk upload actions
            if (FileUploadAction.BULK_UPLOAD_EMPLOYEE.equals(action)) {
                return "/bulk-upload/employee";
            } else if (FileUploadAction.BULK_UPLOAD_ITEMS.equals(action)) {
                return "/bulk-upload/items";
            } else if (FileUploadAction.BULK_UPLOAD_RESTAURANTS.equals(action)) {
                return "/bulk-upload/restaurants";
            }
            // Handle profile image actions
            else if (FileUploadAction.PROFILE_IMAGE_RESTAURANT.equals(action)) {
                return "/profile-images/restaurant";
            } else if (FileUploadAction.PROFILE_IMAGE_RESTAURANT_GROUP.equals(action)) {
                return "/profile-images/restaurant-group";
            } else if (FileUploadAction.PROFILE_IMAGE_EMPLOYEE.equals(action)) {
                return "/profile-images/employee";
            }else if (FileUploadAction.ITEM_IMAGE.equals(action)) {
                return "/restaurant/item-images";
            } else if (FileUploadAction.MODIFIER_ITEM_IMAGE.equals(action)) {
                return "/restaurant/modifier-item-images";
            } else if (FileUploadAction.PROMOTION_IMAGE.equals(action)) {
                return "/restaurant/promotion-images";
            } else if (FileUploadAction.PAYMENT_METHODS.equals(action)) {
                return "/payment/methods";
            } else if (FileUploadAction.PAYMENT_APPS.equals(action)) {
                return "/payment/apps";
            }
            // Handle existing actions
            else if (FileUploadAction.PORTFOLIO.equals(action) || FileUploadAction.REPORTS.equals(action) || FileUploadAction.INVOICE.equals(action)) {
                fileFolder += DOCUMENT + "/";
                if (FileUploadAction.PORTFOLIO.equals(action)) {
                    return fileFolder + FileUploadAction.PORTFOLIO.name().toLowerCase();
                } else if(FileUploadAction.INVOICE.equals(action)){
                    return fileFolder + FileUploadAction.INVOICE.name().toLowerCase();
                }else {
                    return fileFolder + FileUploadAction.REPORTS.name().toLowerCase();
                }
            } else if (FileUploadAction.PROFILE_PHOTO.equals(action)) {
                fileFolder += IMAGE + "/" + FileUploadAction.PROFILE_PHOTO.name().toLowerCase().replace(" ", "_");
            }
        }
        else {
            // Default to the main folder for the attachment type
            if (IMAGE.equalsIgnoreCase(attachmentType)) {
                return fileFolder + IMAGE;
            } else if (AUDIO.equalsIgnoreCase(attachmentType)) {
                return fileFolder + AUDIO;
            } else if (VIDEO.equalsIgnoreCase(attachmentType)) {
                return fileFolder + VIDEO;
            } else if (DOCUMENT.equalsIgnoreCase(attachmentType)) {
                return fileFolder + DOCUMENT;
            }
        }
        return fileFolder;
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replaceAll("[^\\p{L}\\p{N}\\u0E00-\\u0E7F-.]+", "_");
    }

    private static String generateFileName(MultipartFile file, String attachmentType) {
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        extension = (extension != null && !extension.isEmpty()) ? "." + extension : "";
        String filename = StringUtils.stripFilenameExtension(sanitizeFilename(originalFilename)) + "_" + attachmentType + "_" + System.currentTimeMillis() + extension;
        return sanitizeFilename(filename);
    }
}

