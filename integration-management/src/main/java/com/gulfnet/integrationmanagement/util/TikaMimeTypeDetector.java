package com.gulfnet.integrationmanagement.util;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaMimeTypeDetector {

    private final Tika tika = new Tika();

    public String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        }
    }
}
