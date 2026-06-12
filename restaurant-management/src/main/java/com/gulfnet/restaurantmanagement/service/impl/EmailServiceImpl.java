package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.exception.EmailSendException;
import com.gulfnet.shared_library.util.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailSender {

    private final JavaMailSender mailSender;

    /**
     * Sends an HTML email to a single recipient.
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML content)
     * @throws EmailSendException if sending the email fails
     */
    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true indicates HTML content
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}, error: {}", to, e.getMessage(), e);
            throw new EmailSendException("Failed to send email", e);
        }
    }

    /**
     * Sends an HTML email with an attachment to a single recipient.
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML content)
     * @param attachmentFilename the filename of the attachment
     * @param attachmentData the attachment data as a byte array
     * @param attachmentContentType the MIME content type of the attachment (e.g., "application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
     * @throws EmailSendException if sending the email fails
     */
    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, 
                                       String attachmentFilename, byte[] attachmentData, 
                                       String attachmentContentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true indicates HTML content
            
            // Add attachment
            helper.addAttachment(attachmentFilename, 
                    new org.springframework.core.io.ByteArrayResource(attachmentData), 
                    attachmentContentType);
            
            mailSender.send(message);
            log.info("Email with attachment sent successfully to: {}, attachment: {}", to, attachmentFilename);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment to: {}, error: {}", to, e.getMessage(), e);
            throw new EmailSendException("Failed to send email with attachment", e);
        }
    }
} 