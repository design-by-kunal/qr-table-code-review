package com.gulfnet.integrationmanagement.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
public class AttachmentUploadFileConfig {

    @Value("${attachment.photos.upload.maxsize}")
    private String allowedPhotosSize;

    @Value("${attachment.photos.upload.extensions}")
    private List<String> allowedPhotosExt;

    @Value("${attachment.audios.upload.maxsize}")
    private String allowedAudiosSize;

    @Value("${attachment.audios.upload.extensions}")
    private List<String> allowedAudiosExt;

    @Value("${attachment.videos.upload.maxsize}")
    private String allowedVideosSize;

    @Value("${attachment.videos.upload.extensions}")
    private List<String> allowedVideosExt;

    @Value("${attachment.docs.upload.maxsize}")
    private String allowedDocsSize;

    @Value("${attachment.docs.upload.extensions}")
    private List<String> allowedDocsExt;
}
