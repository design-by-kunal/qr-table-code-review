package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.exception.EmailSendingException;
import com.gulfnet.shared_library.util.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderImpl implements EmailSender {

    private final JavaMailSender mailSender;

    /**
     * Sends an HTML email to the specified recipient using JavaMailSender.
     * The email body is treated as HTML content.
     *
     * @param to       the recipient email address
     * @param subject  the email subject line
     * @param htmlBody the HTML content of the email body
     * @throws EmailSendingException if email sending fails due to messaging or other errors
     */
    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        log.info("Attempting to send email to: {}", to);
        log.info("Email subject: {}", subject);
        
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // TRUE = treat text as HTML!
            
            log.info("Sending email via SMTP...");
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new EmailSendingException("Failed to send email to " + to + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage(), e);
            throw new EmailSendingException("Unexpected error sending email to " + to + ": " + e.getMessage(), e);
        }
    }
}
