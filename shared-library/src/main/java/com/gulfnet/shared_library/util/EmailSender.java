package com.gulfnet.shared_library.util;

public interface EmailSender {
    void sendEmail(String to, String subject, String body);
    
    /**
     * Send email with attachment
     * @param to recipient email address
     * @param subject email subject
     * @param body email body (HTML supported)
     * @param attachmentFilename name of the attachment file
     * @param attachmentData byte array containing the attachment data
     * @param attachmentContentType MIME type of the attachment (e.g., "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
     */
    default void sendEmailWithAttachment(String to, String subject, String body, 
                                         String attachmentFilename, byte[] attachmentData, 
                                         String attachmentContentType) {
        // Default implementation throws UnsupportedOperationException
        // Implementations should override this method
        throw new UnsupportedOperationException("Email attachments not supported by this EmailSender implementation");
    }
} 