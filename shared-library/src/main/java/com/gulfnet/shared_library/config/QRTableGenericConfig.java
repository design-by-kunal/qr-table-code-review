package com.gulfnet.shared_library.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
public class QRTableGenericConfig {
    // AWS Configuration
    @Value("${aws.upload.api:}")
    private String awsUploadAPI;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretKey:}")
    private String accessKeySecret;

    @Value("${aws.region:}")
    private String awsRegion;

    @Value("${aws.s3.bucket-name:}")
    private String s3BucketName;

    // Profile Photo Configuration
    @Value("${profilePhoto.user.path:}")
    private String profilePhoto;

    @Value("${profilePhoto.upload.extension:}")
    private List<String> allowedProfileFileExtensions;

    @Value("${profilePhoto.upload.maxSize:}")
    private Long profilePhotoMaxSize;

    @Value("${profilePhoto.storage.base-directory:}")
    private String baseProfilePhotoDirectoryPath;

    // Security Configuration
    @Value("${app.sec.key:}")
    private String appSecurityKey;

    // Validation Configuration
    @Value("${regex.email.regexp:}")
    private String regExEmail;

    @Value("${regex.phone.regexp:}")
    private String regExPhone;

    // Service Icon Configuration
    @Value("${serviceIcon.admin.path:}")
    private String serviceIcon;

    @Value("${serviceIcon.storage.base-directory:}")
    private String serviceIconPath;

    @Value("${serviceIcon.upload.maxSize:}")
    private Long serviceIconMaxSize;

    // Area Configuration
    @Value("${area.measure.weights:}")
    private String areaMeasureWeights;

    // File Storage Configuration
    @Value("${file.storage.base-directory:}")
    private String baseFileDirectory;

    @Value("${file.upload.maxSize:}")
    private Long maxFileUploadSize;

    @Value("${file.upload.extensions:}")
    private List<String> allowedFileExtension;

    // Media Configuration
    @Value("${data.image.baseurl:}")
    private String baseMediaUrl;

    @Value("${data.on.premises:}")
    private Boolean isAppOnPremises;

    // Invoice Configuration
    @Value("${provider.invoice.file.path:}")
    private String providerInvoicePath;

    @Value("${cedent.invoice.file.path:}")
    private String cedentInvoicePath;

    // Damage Report Configuration
    @Value("${damage.report.file.path:}")
    private String damageReportPath;

    public QRTableGenericConfig() {
        // Initialize default values for lists
        this.allowedProfileFileExtensions = Arrays.asList("png", "gif", "jpeg", "jpg");
        this.allowedFileExtension = Arrays.asList("png", "jpeg", "jpg", "csv", "xlsx", "xls", "pdf", "mp3", "mp4");
    }
} 