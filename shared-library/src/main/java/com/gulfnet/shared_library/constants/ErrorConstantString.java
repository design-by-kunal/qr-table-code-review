package com.gulfnet.shared_library.constants;

public class ErrorConstantString {
    
    private ErrorConstantString() {
        // Utility class - prevent instantiation
    }
    
    public static String notValidErrorMessageFileType(String extension) {
        return "File type " + extension + " is not allowed";
    }
    
    public static String notValidErrorMessageFileTypeWithAllowed(String allowedExtensions) {
        return "File type is not allowed. Allowed extensions: " + allowedExtensions;
    }
    
    public static String notValidErrorMessageFileSize(String fileSize) {
        return "File size " + fileSize + " exceeds the maximum allowed size";
    }
    
    public static String notValidErrorMessageFileSizeWithMax(String maxSize) {
        return "File size exceeds the maximum allowed size of " + maxSize;
    }
} 