package com.gulfnet.shared_library.util;

import com.gulfnet.shared_library.config.QRTableGenericConfig;
import com.gulfnet.shared_library.constants.ErrorConstantString;
import com.gulfnet.shared_library.exception.ValidationException;
import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FileUploadValidationUtil {

    private final QRTableGenericConfig qrTableGenericConfig;

    /**
     * Validates a profile photo file by checking its extension and file size.
     * Validates against allowed profile file extensions and maximum profile photo size
     * configured in the application properties.
     *
     * @param file the multipart file to validate
     * @throws ValidationException if the file extension is not allowed or file size exceeds the maximum
     */
    public void validateProfilePhotoFile(MultipartFile file) {
        List<ErrorDto> errors = new ArrayList<>();

        String extension = getFileExtension(file).toLowerCase();
        if (!qrTableGenericConfig.getAllowedProfileFileExtensions().contains(extension)) {
            errors.add(new ErrorDto(String.valueOf(HttpStatus.UNPROCESSABLE_ENTITY),
                    ErrorConstantString.notValidErrorMessageFileType(extension)));
        }
        if (file.getSize() > qrTableGenericConfig.getProfilePhotoMaxSize()) {
            errors.add(new ErrorDto(String.valueOf(HttpStatus.UNPROCESSABLE_ENTITY),
                     ErrorConstantString.notValidErrorMessageFileSize(String.valueOf(file.getSize()))));
        }

        if (!errors.isEmpty()) throw new ValidationException(errors);

    }

    public String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null ? StringUtils.getFilenameExtension(originalFilename) : null;
    }

    /**
     * Validates a file by checking its extension and file size.
     * Validates against allowed file extensions and maximum file upload size
     * configured in the application properties.
     *
     * @param file the multipart file to validate
     * @throws ValidationException if the file extension is not allowed or file size exceeds the maximum
     */
    public void validate(MultipartFile file) {
        List<ErrorDto> errors = new ArrayList<>();

        String extension = getFileExtension(file).toLowerCase();
        if (!qrTableGenericConfig.getAllowedFileExtension().contains(extension)) {
            errors.add(new ErrorDto(HttpStatus.BAD_REQUEST,
                    ErrorConstantString.notValidErrorMessageFileTypeWithAllowed(Arrays.toString(qrTableGenericConfig.getAllowedFileExtension().toArray()))));
        }
        if (file.getSize() > qrTableGenericConfig.getMaxFileUploadSize()) {
            errors.add(new ErrorDto(HttpStatus.BAD_REQUEST,
                    ErrorConstantString.notValidErrorMessageFileSizeWithMax(String.valueOf(qrTableGenericConfig.getMaxFileUploadSize()))));
        }

        if (!errors.isEmpty()) throw new ValidationException(String.valueOf(errors));
    }

    public String getOnPremisesUrl(String filePath) {
        if (filePath == null || filePath.isEmpty() || filePath.equalsIgnoreCase("location") ) {
            return "";
        }
        return qrTableGenericConfig.getBaseMediaUrl() + filePath;
    }
}
