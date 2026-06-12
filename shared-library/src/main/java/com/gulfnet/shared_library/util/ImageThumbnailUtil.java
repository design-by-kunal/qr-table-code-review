package com.gulfnet.shared_library.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class ImageThumbnailUtil {

    private static final int DEFAULT_MAX_WIDTH = 300;
    private static final int DEFAULT_MAX_HEIGHT = 300;

    /**
     * Creates a thumbnail image from the original image bytes, scaling it down to fit
     * within the default maximum dimensions (300x300) while maintaining aspect ratio.
     * The image will not be upscaled if it's already smaller than the maximum dimensions.
     *
     * @param originalBytes the original image data as a byte array
     * @param formatName    the image format name (e.g., "jpg", "png", "image/jpeg")
     * @return the thumbnail image data as a byte array
     * @throws IOException if the image data is empty, unsupported format, or processing fails
     */
    public byte[] createThumbnail(byte[] originalBytes, String formatName) throws IOException {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IOException("Empty image data");
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(originalBytes)) {
            BufferedImage original = ImageIO.read(input);
            if (original == null) {
                throw new IOException("Unsupported image format");
            }

            int width = original.getWidth();
            int height = original.getHeight();

            double scale = Math.min((double) DEFAULT_MAX_WIDTH / width, (double) DEFAULT_MAX_HEIGHT / height);
            if (scale > 1.0) {
                scale = 1.0; // don't upscale
            }

            int targetWidth = (int) Math.round(width * scale);
            int targetHeight = (int) Math.round(height * scale);

            Image scaled = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = thumbnail.createGraphics();
            g2d.drawImage(scaled, 0, 0, null);
            g2d.dispose();

            String outFormat = normalizeFormat(formatName);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                boolean written = ImageIO.write(thumbnail, outFormat, output);
                if (!written) {
                    // fallback to png
                    ImageIO.write(thumbnail, "png", output);
                }
                return output.toByteArray();
            }
        }
    }

    /**
     * Normalizes an image format string to a standard format name.
     * Handles MIME types (e.g., "image/jpeg" -> "jpg"), converts "jpeg" to "jpg",
     * and validates against supported formats (jpg, png, gif, bmp, webp).
     * Returns "png" as the default format if the input is null or unsupported.
     *
     * @param input the format string to normalize (can be MIME type or format name)
     * @return normalized format name (jpg, png, gif, bmp, webp, or "png" as default)
     */
    private String normalizeFormat(String input) {
        if (input == null) return "png";
        String f = input.toLowerCase();
        if (f.startsWith("image/")) {
            f = f.substring(6);
        }
        if (f.equals("jpeg")) return "jpg";
        if (f.equals("jpg") || f.equals("png") || f.equals("gif") || f.equals("bmp") || f.equals("webp")) {
            return f;
        }
        return "png";
    }
}


