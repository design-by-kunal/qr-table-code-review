package com.gulfnet.shared_library.util;

/**
 * Filename helpers shared by bulk-upload flows (ZIP image extraction, unique names, sanitization).
 */
public final class BulkUploadImageFilenameUtils {

    private BulkUploadImageFilenameUtils() {
    }

    public static boolean isValidImageFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")
                || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif")
                || lowerFileName.endsWith(".bmp") || lowerFileName.endsWith(".webp");
    }

    public static String generateBulkUploadImageFileName(String originalFileName) {
        String extension = getFileExtension(originalFileName);
        extension = (extension != null && !extension.isEmpty()) ? "." + extension : "";

        String baseName = stripFileExtension(sanitizeFilename(originalFileName));
        long timestamp = System.currentTimeMillis();

        String filename = baseName + "_" + timestamp + extension;
        return sanitizeFilename(filename);
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    public static String stripFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    public static String sanitizeFilename(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
