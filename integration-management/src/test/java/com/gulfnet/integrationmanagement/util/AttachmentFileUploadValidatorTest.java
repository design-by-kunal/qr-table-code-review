package com.gulfnet.integrationmanagement.util;

import com.gulfnet.integrationmanagement.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttachmentFileUploadValidatorTest {

    private static final byte[] MINIMAL_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            (byte) 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private static final byte[] MINIMAL_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
    };

    @Mock
    private AttachmentUploadFileConfig attachmentUploadFileConfig;

    private AttachmentFileUploadValidator validator;

    @BeforeEach
    void setUp() {
        when(attachmentUploadFileConfig.getAllowedPhotosExt()).thenReturn(List.of("png", "jpeg", "jpg", "gif"));
        when(attachmentUploadFileConfig.getAllowedAudiosExt()).thenReturn(List.of("mp3", "wav", "aac"));
        when(attachmentUploadFileConfig.getAllowedVideosExt()).thenReturn(List.of("mp4", "avi", "mov"));
        when(attachmentUploadFileConfig.getAllowedDocsExt()).thenReturn(List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv"));
        when(attachmentUploadFileConfig.getAllowedPhotosSize()).thenReturn("5000000");
        when(attachmentUploadFileConfig.getAllowedAudiosSize()).thenReturn("10000000");
        when(attachmentUploadFileConfig.getAllowedVideosSize()).thenReturn("50000000");
        when(attachmentUploadFileConfig.getAllowedDocsSize()).thenReturn("10000000");

        validator = new AttachmentFileUploadValidator(attachmentUploadFileConfig, new TikaMimeTypeDetector());
    }

    @Test
    void validate_acceptsValidPngWithMatchingContent() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", MINIMAL_PNG);

        String mimeType = validator.validate(file);

        assertThat(mimeType).isEqualTo("image/png");
    }

    @Test
    void validate_rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", MINIMAL_PNG);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorMessage", "File type validation error");
    }

    @Test
    void validate_rejectsExtensionContentMismatch() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", MINIMAL_JPEG);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorMessage", "File type validation error");
    }

    @Test
    void validate_rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", MINIMAL_PNG) {
            @Override
            public long getSize() {
                return 6_000_000L;
            }
        };

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorMessage", "File size validation error");
    }
}
