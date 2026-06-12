package com.gulfnet.integrationmanagement.util;

import java.util.Map;
import java.util.Set;

final class AllowedMimeTypes {

    private static final Map<String, Set<String>> EXTENSION_TO_MIME = Map.ofEntries(
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("mp3", Set.of("audio/mpeg", "audio/mp3")),
            Map.entry("wav", Set.of("audio/wav", "audio/x-wav", "audio/vnd.wave")),
            Map.entry("aac", Set.of("audio/aac", "audio/x-aac", "audio/vnd.dlna.adts")),
            Map.entry("mp4", Set.of("video/mp4")),
            Map.entry("avi", Set.of("video/x-msvideo", "video/vnd.avi", "video/avi")),
            Map.entry("mov", Set.of("video/quicktime")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip"
            )),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/zip"
            )),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/csv", "text/plain", "application/csv"))
    );

    private AllowedMimeTypes() {
    }

    static boolean isAllowed(String extension, String detectedMimeType) {
        if (extension == null || detectedMimeType == null) {
            return false;
        }
        Set<String> allowedMimes = EXTENSION_TO_MIME.get(extension.toLowerCase());
        return allowedMimes != null && allowedMimes.contains(detectedMimeType);
    }
}
