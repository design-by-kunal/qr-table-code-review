package com.gulfnet.shared_library.config;

import com.amazonaws.AmazonClientException;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.gulfnet.shared_library.exception.AWSConfigurationException;
import com.gulfnet.shared_library.exception.FileDeletionException;
import com.gulfnet.shared_library.exception.FileDownloadException;
import com.gulfnet.shared_library.exception.FileUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Date;
import com.amazonaws.services.s3.model.S3Object;

@Slf4j
@Service
@RequiredArgsConstructor
public class AWSService {

    /**
     * Constants for error messages.
     * Following industry standard practice of using constants for error messages.
     */
    private static class ErrorMessages {
        static final String AWS_CONFIGURATION_MISSING = "AWS configuration is missing. Please configure AWS properties.";
        static final String FAILED_TO_UPLOAD_FILE = "Failed to upload file to S3";
        static final String ERROR_STORING_FILE = "Error storing file to S3 and generating presigned URL";
        
        private ErrorMessages() {
            // Utility class - prevent instantiation
        }
    }

    private final QRTableGenericConfig qrtableGenericConfig;
    private final S3Config s3Config;
    private final int preSignedURLValidity = 1440;  //In minutes

    public String uploadFile(InputStream inputStream, String key, long contentLength) {
        return uploadFile(inputStream, key, contentLength, null);
    }

    /**
     * Uploads a file to Amazon S3 and returns the S3 object key (without bucket/domain).
     * The input stream is automatically buffered if it doesn't support marking/resetting.
     *
     * @param inputStream   the input stream containing the file data
     * @param key          the S3 object key (path) where the file will be stored
     * @param contentLength the size of the file in bytes
     * @param contentType  optional content type (MIME type) of the file, null if not specified
     * @return the S3 object key (e.g. "restaurant/item-images/file.jpg")
     * @throws AWSConfigurationException if AWS is not properly configured
     * @throws FileUploadException if the upload fails
     */
    public String uploadFile(InputStream inputStream, String key, long contentLength, String contentType) {
        if (!isAwsConfigured()) {
            throw new AWSConfigurationException(ErrorMessages.AWS_CONFIGURATION_MISSING);
        }

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }

        // Ensure the input stream supports marking and resetting for the SDK's retries
        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }
        int readLimit = (int) contentLength + 1;
        inputStream.mark(readLimit);  // Marking slightly more than contentLength

        try {
            PutObjectRequest putRequest = new PutObjectRequest(qrtableGenericConfig.getS3BucketName(), key, inputStream, metadata);
            putRequest.getRequestClientOptions().setReadLimit(readLimit);
            s3Config.s3client().putObject(putRequest);
            return key;
        } catch (AmazonClientException ace) {
            log.error("Error during upload to S3", ace);
            throw new FileUploadException(ErrorMessages.FAILED_TO_UPLOAD_FILE, ace);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.error("Error closing input stream", e);
            }
        }
    }

    /**
     * Generates a pre-signed URL for accessing an S3 object.
     * Accepts both full S3 URLs (legacy) and plain S3 keys (new format).
     * The URL is valid for the duration specified by preSignedURLValidity (default 1440 minutes).
     *
     * @param objectKey the S3 object key or full S3 URL
     * @return the pre-signed URL as a string, or empty string if objectKey is null, empty, or "location"
     */
    public String getPreSignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isEmpty() || objectKey.equalsIgnoreCase("location")) {
            return "";
        }

        String s3Key = stripToKey(objectKey);

        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * preSignedURLValidity;
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                qrtableGenericConfig.getS3BucketName(), s3Key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);

        URL url = s3Config.s3client().generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    /**
     * Generate a presigned URL for PDF files with proper headers for inline preview.
     * Accepts both full S3 URLs (legacy) and plain S3 keys (new format).
     * Sets Content-Type: application/pdf and Content-Disposition: inline
     */
    public String getPreSignedUrlForPdf(String objectKey) {
        if (objectKey == null || objectKey.isEmpty() || objectKey.equalsIgnoreCase("location")) {
            return "";
        }

        String s3Key = stripToKey(objectKey);

        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * preSignedURLValidity;
        expiration.setTime(expTimeMillis);

        ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
        responseHeaders.setContentType("application/pdf");
        responseHeaders.setContentDisposition("inline");

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                qrtableGenericConfig.getS3BucketName(), s3Key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration)
                .withResponseHeaders(responseHeaders);

        URL url = s3Config.s3client().generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    /**
     * Generate a presigned URL for PDF files with proper headers for download.
     * Accepts both full S3 URLs (legacy) and plain S3 keys (new format).
     * Sets Content-Type: application/pdf and Content-Disposition: attachment
     */
    public String getPreSignedUrlForPdfAttachment(String objectKey) {
        if (objectKey == null || objectKey.isEmpty() || objectKey.equalsIgnoreCase("location")) {
            return "";
        }

        String s3Key = stripToKey(objectKey);

        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * preSignedURLValidity;
        expiration.setTime(expTimeMillis);

        ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
        responseHeaders.setContentType("application/pdf");
        responseHeaders.setContentDisposition("attachment");

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                qrtableGenericConfig.getS3BucketName(), s3Key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration)
                .withResponseHeaders(responseHeaders);

        URL url = s3Config.s3client().generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    /**
     * Normalizes a full S3 URL or plain S3 key into just the S3 object key.
     * Handles both legacy full URLs (https://bucket.s3.region.amazonaws.com/key)
     * and new plain keys (key). Returns the value as-is if it's already a plain key.
     *
     * @param urlOrKey a full S3 URL or a plain S3 object key
     * @return the S3 object key without bucket/domain prefix, or null if input is null
     */
    public String stripToKey(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isEmpty()) {
            return urlOrKey;
        }
        if (urlOrKey.contains("amazonaws.com/")) {
            try {
                // URL-decode the path part so we don't double-encode %xx sequences
                // e.g. %E5... -> 和 -> pass "和" as the S3 key so the SDK encodes it only once.
                URL url = new URL(urlOrKey);
                String path = url.getPath();
                String key = path.startsWith("/") ? path.substring(1) : path;
                if (key != null && key.contains("%")) {
                    key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
                }
                return key;
            } catch (Exception e) {
                // Fallback: best-effort substring extraction, then URL-decode if it looks encoded.
                String key = urlOrKey.substring(urlOrKey.indexOf("amazonaws.com/") + "amazonaws.com/".length());
                if (key.startsWith("/")) {
                    key = key.substring(1);
                }
                if (key != null && key.contains("%")) {
                    try {
                        key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        // Keep the extracted key as-is.
                    }
                }
                return key;
            }
        }
        if (urlOrKey.startsWith("http")) {
            try {
                URL url = new URL(urlOrKey);
                String path = url.getPath();
                return path.startsWith("/") ? path.substring(1) : path;
            } catch (Exception e) {
                log.warn("Could not parse URL, treating as key: {}", urlOrKey);
            }
        }
        return urlOrKey.startsWith("/") ? urlOrKey.substring(1) : urlOrKey;
    }

    /**
     * Constructs the full S3 URL from a plain key or returns the value as-is if already a full URL.
     * The bucket name and region are resolved from application.properties at runtime.
     *
     * @param keyOrUrl a plain S3 key or a full S3 URL (legacy)
     * @return the full S3 URL, or null/empty preserving the original input
     */
    public String getFullUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isEmpty()) {
            return keyOrUrl;
        }
        if (keyOrUrl.startsWith("http")) {
            return keyOrUrl;
        }
        String bucketName = qrtableGenericConfig.getS3BucketName();
        String region = qrtableGenericConfig.getAwsRegion();
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + keyOrUrl;
    }

    public static String extractFileName(String urlString) {
        try {
            if (urlString == null) return "/";
            URL url = new URL(urlString);
            return Paths.get(url.getPath()).toString();
        } catch (Exception e) {
            log.error("Failed to extract FileName. urlString-" + urlString);
            return (urlString != null && !urlString.startsWith("/")) ? "/" + urlString : (urlString != null ? urlString : "/");
        }
    }

    /**
     * Uploads a multipart file to S3 and immediately generates a pre-signed URL for it.
     * The pre-signed URL expiration is set to the specified duration in minutes.
     *
     * @param file                    the multipart file to upload
     * @param key                     the S3 object key (path) where the file will be stored
     * @param expirationDurationMinutes the expiration duration for the pre-signed URL in minutes
     * @return the pre-signed URL for accessing the uploaded file
     * @throws FileUploadException if the upload fails
     */
    public String uploadFileAndGetPreSignedURL(MultipartFile file, String key, int expirationDurationMinutes) {
        String fileName = file.getOriginalFilename();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            s3Config.s3client().putObject(new PutObjectRequest(qrtableGenericConfig.getS3BucketName(), key, file.getInputStream(), metadata));

            // Set expiration time
            Date expiration = new Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000 * 60 * expirationDurationMinutes;
            expiration.setTime(expTimeMillis);

            // Generate pre-signed URL
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(qrtableGenericConfig.getS3BucketName(), fileName).withMethod(HttpMethod.GET).withExpiration(expiration);
            URL url = s3Config.s3client().generatePresignedUrl(generatePresignedUrlRequest);

            return url.toString();
        } catch (IOException e) {
            throw new FileUploadException(ErrorMessages.ERROR_STORING_FILE, e);
        }
    }

    /**
     * Downloads a file from S3 and returns its contents as a byte array.
     *
     * @param s3Key the S3 object key (path) of the file to download
     * @return the file contents as a byte array
     * @throws AWSConfigurationException if AWS is not properly configured
     * @throws FileDownloadException if the download fails
     */
    public byte[] downloadFileFromS3(String s3Key) {
        if (!isAwsConfigured()) {
            throw new AWSConfigurationException(ErrorMessages.AWS_CONFIGURATION_MISSING);
        }

        String resolvedKey = stripToKey(s3Key);
        try (S3Object s3Object = s3Config.s3client().getObject(qrtableGenericConfig.getS3BucketName(), resolvedKey);
             InputStream inputStream = s3Object.getObjectContent()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            log.error("Error downloading file from S3", e);
            throw new FileDownloadException("Failed to download file from S3", e);
        }
    }

    private boolean isAwsConfigured() {
        return qrtableGenericConfig.getAccessKeyId() != null && !qrtableGenericConfig.getAccessKeyId().isEmpty() &&
               qrtableGenericConfig.getAccessKeySecret() != null && !qrtableGenericConfig.getAccessKeySecret().isEmpty() &&
               qrtableGenericConfig.getAwsRegion() != null && !qrtableGenericConfig.getAwsRegion().isEmpty() &&
               qrtableGenericConfig.getS3BucketName() != null && !qrtableGenericConfig.getS3BucketName().isEmpty();
    }

    /**
     * Deletes a file from S3 using the specified object key.
     *
     * @param s3Key the S3 object key (path) of the file to delete
     * @throws AWSConfigurationException if AWS is not properly configured
     * @throws FileDeletionException if the deletion fails
     */
    public void deleteFile(String s3Key) {
        if (!isAwsConfigured()) {
            throw new AWSConfigurationException(ErrorMessages.AWS_CONFIGURATION_MISSING);
        }

        String resolvedKey = stripToKey(s3Key);
        try {
            s3Config.s3client().deleteObject(new DeleteObjectRequest(qrtableGenericConfig.getS3BucketName(), resolvedKey));
        } catch (Exception e) {
            log.error("Failed to delete S3 object with key: " + resolvedKey, e);
            throw new FileDeletionException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

}
