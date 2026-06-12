package com.gulfnet.restaurantmanagement.util;

/**
 * Utility methods for working with file paths and names.
 */
public final class FileNameUtil {

    private FileNameUtil() {
        // Utility class
    }

    /**
     * Extract the file name from a full path, supporting both '/' and '\\' separators.
     *
     * @param filePath the full file path
     * @return the file name, or {@code null} if the input is null or empty
     */
    public static String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        int lastSlashIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlashIndex >= 0 ? filePath.substring(lastSlashIndex + 1) : filePath;
    }
}

