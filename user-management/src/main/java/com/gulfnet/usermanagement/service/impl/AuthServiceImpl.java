package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.UserPasswordAudit;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.ChangePasswordRequest;
import com.gulfnet.shared_library.model.request.ForgotPasswordRequest;
import com.gulfnet.shared_library.model.request.VerifyOTPRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OtpMetadataResponse;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.UserPasswordAuditRepository;
import com.gulfnet.shared_library.repository.LoginAuditRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.usermanagement.service.AuthService;
import com.gulfnet.usermanagement.service.NotificationPublisherService;
import com.gulfnet.usermanagement.util.JwtUtil;
import com.gulfnet.usermanagement.util.MessageUtil;
import com.gulfnet.usermanagement.config.EmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.concurrent.Executor;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    /**
     * Constants for password audit status values.
     * Following industry standard practice of using constants for status values.
     */
    private static class AuditStatus {
        static final String COMPLETED = "COMPLETED";
        static final String PENDING = "PENDING";
        static final String EXPIRED = "EXPIRED";
        
        private AuditStatus() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for password audit action values.
     * Following industry standard practice of using constants for action values.
     */
    private static class AuditAction {
        static final String RESET_PASSWORD = "RESET_PASSWORD";
        static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
        
        private AuditAction() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for role names.
     * Following industry standard practice of using constants for role values.
     */
    private static class RoleNames {
        static final String WAITER = "WAITER";
        static final String MANAGER = "MANAGER";
        static final String UNKNOWN = "UNKNOWN";
        
        private RoleNames() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for notification types.
     * Following industry standard practice of using constants for notification types.
     */
    private static class NotificationTypes {
        static final String PASSWORD_UPDATED = "PASSWORD_UPDATED";
        
        private NotificationTypes() {
            // Utility class - prevent instantiation
        }
    }

    private static class AuthEmailHtml {
        static final String TD_CLOSE = "</td>";
        static final String TR_CLOSE = "</tr>";
        static final String TABLE_CLOSE = "</table>";
        static final String TD_LABEL_ROW = "<td style=\"font-size:14px;color:#6b7280;padding:4px 0;\">";
        static final String TD_VALUE_ROW = "<td align=\"right\" style=\"font-size:14px;color:#111827;font-weight:700;padding:4px 0;\">";

        private AuthEmailHtml() {
            // Utility class - prevent instantiation
        }
    }

    private static class AuthEmailMessageKeys {
        static final String FORGOT_PASSWORD_WAITER_NEW_PASSWORD_SUBJECT = "forgot.password.waiter.new.password.subject";
        static final String FORGOT_PASSWORD_WAITER_NEW_PASSWORD_BODY = "forgot.password.waiter.new.password.body";

        private AuthEmailMessageKeys() {
            // Utility class - prevent instantiation
        }
    }

    private final UserRepository userRepository;
    private final UserPasswordAuditRepository userPasswordAuditRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final JwtUtil jwtUtil;
    private final MessageUtil messageUtil;
    private final EmailProperties emailProperties;
    private final NotificationPublisherService notificationPublisherService;
    @Qualifier("emailTaskExecutor")
    private final Executor emailTaskExecutor;

    /**
     * Changes the password for the authenticated user identified by the provided JWT token.
     * Validates the current password, enforces password complexity and difference from the old one,
     * updates the password and related audit records, invalidates existing sessions, and notifies
     * the user via email and push notification.
     *
     * @param token   the JWT token for the authenticated user
     * @param request the change password request containing current, new, and confirm passwords
     * @return {@link ResponseDto} with a localized success message
     */
    @Override
    @Transactional
    public ResponseDto<String> changePassword(String token, ChangePasswordRequest request) {
        // Extract user from JWT token
        String userId = jwtUtil.getUserIdFromToken(token);
        Locale userLocale = LocaleContextHolder.getLocale();
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        
        // Validate required fields
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.current.required", userLocale));
        }
        
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.new.required", userLocale));
        }
        
        if (request.getConfirmPassword() == null || request.getConfirmPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.confirm.required", userLocale));
        }
        
        // Validate current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.current.incorrect", userLocale));
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("password.same.as.old", userLocale));
        }

        
        // Validate new password
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());
        
        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        
        // Create password audit
        UserPasswordAudit audit = UserPasswordAudit.builder()
                .user(user)
                .action(AuditAction.CHANGE_PASSWORD)
                .status(AuditStatus.COMPLETED)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        userPasswordAuditRepository.save(audit);
        
        // Invalidate all sessions
        loginAuditRepository.deleteByUser_Id(user.getId());
        
        // Send email notification
        sendPasswordChangeEmail(user);
        
        // Send FCM notification to user about password change
        sendPasswordChangeNotification(user, userLocale);
        
        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("password.change.success", userLocale))
                .data(null)
                .build();
    }
    
    /**
     * Initiates the password reset flow for a user identified by email or user code.
     * For waiters, immediately generates and applies a new password and sends it to
     * managers or a default email; for other roles, generates an OTP, records it in
     * the audit table, and sends it to the appropriate recipient.
     *
     * @param request the forgot password request containing email and/or user code
     * @return {@link ResponseDto} with {@link OtpMetadataResponse} or null depending on role
     */
    @Override
    @Transactional
    public ResponseDto<OtpMetadataResponse> forgotPassword(ForgotPasswordRequest request) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find the user who requested password reset
        User user = null;
        
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            user = userRepository.findByEmailIgnoreCase(request.getEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("forgot.password.error.user.not.found", userLocale)));
        } else if (request.getUserCode() != null && !request.getUserCode().trim().isEmpty()) {
            // Use case-insensitive lookup for userCode
            String normalizedUserCode = request.getUserCode().trim().toLowerCase();
            user = userRepository.findByUserCodeIgnoreCaseAndIsDeletedFalseAndStatus(
                    normalizedUserCode, EntityStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("forgot.password.error.user.not.found", userLocale)));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.login.error.email.or.usercode.required", userLocale));
        }
        
        // Get user's role
        String userRole = RoleNames.UNKNOWN;
        if (user.getRoleId() != null) {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            if (role != null) {
                userRole = role.getName();
            }
        }

        // Validate that the requested appType matches the user's role.
        // Comparison is case-insensitive and ignores underscores so that,
        // for example, HQADMIN (AppType) and HQ_ADMIN (DB role) are treated as equal.
        if (request.getAppType() != null && userRole != null) {
            String normalizedRole = userRole.replace("_", "").toUpperCase();
            String normalizedAppType = request.getAppType().name().replace("_", "").toUpperCase();
            if (!normalizedRole.equals(normalizedAppType)) {
                // Example scenario: HQ_ADMIN trying to reset password from MANAGER site.
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        messageUtil.getMessage("forgot.password.error.apptype.role.mismatch", userLocale)
                );
            }
        }
        
        // Handle password reset based on user role
        if (RoleNames.WAITER.equals(userRole)) {
            // For waiters, generate new password and send to manager or HQ admin
            String newPassword = generateNewPassword();
            
            // Update user's password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userRepository.save(user);
            
            // Create password reset audit
            userPasswordAuditRepository.save(buildCompletedPasswordResetAudit(user));
            
            // Invalidate all sessions
            loginAuditRepository.deleteByUser_Id(user.getId());
            
            // Build recipient list synchronously (validation) and send email asynchronously after commit.
            // This keeps API latency low while preserving rollback safety for recipient resolution errors.
            List<PreparedEmail> preparedWaiterEmails = buildWaiterNewPasswordEmails(user, newPassword, userLocale);
            schedulePreparedEmailsAfterCommit(preparedWaiterEmails, user.getId(), "waiter new password");
            
            // Send FCM notification to user about password change
            sendPasswordChangeNotification(user, userLocale);
            
            return ResponseDto.<OtpMetadataResponse>builder()
                    .message(messageUtil.getMessage("forgot.password.waiter.success", userLocale))
                    .data(null)
                    .build();
        } else {
            // For other roles, use OTP flow
            String otp = generateOTP();
            
            // Calculate expiry time (10 minutes from now)
            OffsetDateTime otpExpiryDate = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
            
            // Create OTP audit record
            UserPasswordAudit otpAudit = UserPasswordAudit.builder()
                    .user(user)
                    .otp(Long.parseLong(otp))
                    .otpExpiryDate(otpExpiryDate)
                    .action(AuditAction.RESET_PASSWORD)
                    .status(AuditStatus.PENDING)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            userPasswordAuditRepository.save(otpAudit);
            
            // Send OTP after DB commit so the pending OTP row is visible before SMTP runs;
            // avoids holding the transaction open for multi-second SMTP latency.
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                PreparedEmail prepared = buildOtpEmailForUser(user, otp);
                schedulePreparedEmailAfterCommit(prepared, user.getId(), "OTP");
            } else {
                PreparedEmail prepared = buildWaiterPasswordResetOtpEmail(user, otp);
                schedulePreparedEmailAfterCommit(prepared, user.getId(), "waiter password reset OTP");
            }
            
            return ResponseDto.<OtpMetadataResponse>builder()
                    .message(messageUtil.getMessage("otp.sent.success", userLocale))
                    .data(OtpMetadataResponse.builder()
                            .expiresAt(otpExpiryDate)
                            .build())
                    .build();
        }
    }
    
    /**
     * Verifies a submitted OTP and, if valid and not expired, updates the user's password
     * after validating password complexity and difference from the previous one. Also updates
     * audit records, invalidates sessions, and sends confirmation notifications.
     *
     * @param request the OTP verification request containing email, OTP, and new passwords
     * @return {@link ResponseDto} with a localized success message
     */
    @Override
    @Transactional
    public ResponseDto<String> verifyOTP(VerifyOTPRequest request) {
        // Find the user
        Locale userLocale = LocaleContextHolder.getLocale();
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        

        // Find the latest pending OTP for this user
        Optional<UserPasswordAudit> pendingOtpOpt = userPasswordAuditRepository
                .findTopByUserAndActionAndStatusOrderByCreatedAtDesc(user, AuditAction.RESET_PASSWORD, AuditStatus.PENDING);

        if (pendingOtpOpt.isEmpty()) {
            // If no pending OTP, check if there is an expired OTP for this user
            List<UserPasswordAudit> expiredOtps = userPasswordAuditRepository
                    .findByUserIdAndStatusAndActionOrderByCreatedAtDesc(user.getId(), AuditStatus.EXPIRED, AuditAction.RESET_PASSWORD);
            if (!expiredOtps.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("otp.expired", userLocale)
                );
            }
            // Otherwise, no pending OTP exists
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("otp.not.found", userLocale)
            );
        }
        
        UserPasswordAudit otpAudit = pendingOtpOpt.get();
        
        // Check if OTP has expired
        if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(otpAudit.getOtpExpiryDate())) {
            // Mark OTP as expired
            otpAudit.setStatus(AuditStatus.EXPIRED);
            otpAudit.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userPasswordAuditRepository.save(otpAudit);
            
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("otp.expired", userLocale)
            );
        }
        
        // Verify OTP
        if (!String.valueOf(otpAudit.getOtp()).equals(request.getOtp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("otp.invalid", userLocale));
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("password.same.as.old", userLocale));
        }
        
        // Validate new password
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());
        
        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        
        // Mark OTP as completed
        otpAudit.setStatus(AuditStatus.COMPLETED);
        otpAudit.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userPasswordAuditRepository.save(otpAudit);
        
        // Create password reset audit
        userPasswordAuditRepository.save(buildCompletedPasswordResetAudit(user));
        
        // Invalidate all sessions
        loginAuditRepository.deleteByUser_Id(user.getId());
        
        // Send password reset success email
        sendPasswordResetEmail(user);
        
        // Send FCM notification to user about password change
        sendPasswordChangeNotification(user, userLocale);
        
        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("otp.verify.success", userLocale))
                .data(null)
                .build();
    }
    
    /**
     * Scheduled task to clean up password audit records
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    @Transactional
    public void cleanupExpiredOTPs() {
        log.info("Starting cleanup of password audit records...");
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime twentyFourHoursAgo = now.minusHours(24);
        
        // ===== RESET_PASSWORD RECORDS =====
        
        // Mark pending OTPs as expired if they've passed expiry time
        List<UserPasswordAudit> expiredOTPs = userPasswordAuditRepository
                .findByActionAndStatusAndOtpExpiryDateBefore(AuditAction.RESET_PASSWORD, AuditStatus.PENDING, now);
        
        for (UserPasswordAudit otpAudit : expiredOTPs) {
            otpAudit.setStatus(AuditStatus.EXPIRED);
            otpAudit.setUpdatedAt(now);
            userPasswordAuditRepository.save(otpAudit);
            log.info("Marked expired OTP for user id: {}",
                    otpAudit.getUser() != null ? otpAudit.getUser().getId() : otpAudit.getId());
        }
        
        // Delete completed RESET_PASSWORD records older than 24 hours
        List<UserPasswordAudit> oldCompletedResetOTPs = userPasswordAuditRepository
                .findByActionAndStatusAndCreatedAtBefore(AuditAction.RESET_PASSWORD, AuditStatus.COMPLETED, twentyFourHoursAgo);
        
        userPasswordAuditRepository.deleteAll(oldCompletedResetOTPs);
        log.info("Deleted {} old completed RESET_PASSWORD records", oldCompletedResetOTPs.size());
        
        // Delete expired RESET_PASSWORD records older than 24 hours
        List<UserPasswordAudit> oldExpiredResetOTPs = userPasswordAuditRepository
                .findByActionAndStatusAndCreatedAtBefore(AuditAction.RESET_PASSWORD, AuditStatus.EXPIRED, twentyFourHoursAgo);
        
        userPasswordAuditRepository.deleteAll(oldExpiredResetOTPs);
        log.info("Deleted {} old expired RESET_PASSWORD records", oldExpiredResetOTPs.size());
        
        // ===== CHANGE_PASSWORD RECORDS =====
        
        // Delete completed CHANGE_PASSWORD records older than 24 hours
        List<UserPasswordAudit> oldCompletedChangePassword = userPasswordAuditRepository
                .findByActionAndStatusAndCreatedAtBefore(AuditAction.CHANGE_PASSWORD, AuditStatus.COMPLETED, twentyFourHoursAgo);
        
        userPasswordAuditRepository.deleteAll(oldCompletedChangePassword);
        log.info("Deleted {} old completed CHANGE_PASSWORD records", oldCompletedChangePassword.size());
        
        log.info("Password audit cleanup completed. Marked as expired: {}, Deleted RESET_PASSWORD completed: {}, Deleted RESET_PASSWORD expired: {}, Deleted CHANGE_PASSWORD completed: {}", 
                expiredOTPs.size(), oldCompletedResetOTPs.size(), oldExpiredResetOTPs.size(), oldCompletedChangePassword.size());
    }
    
    /**
     * Validates that the new password and confirmation match and that the new password
     * is at least 6 characters.
     *
     * @param newPassword      the proposed new password
     * @param confirmPassword  the confirmation password
     * @throws ResponseStatusException if passwords do not match or fail length rules
     */
    private void validateNewPassword(String newPassword, String confirmPassword) {
        Locale userLocale = LocaleContextHolder.getLocale();
        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.confirm.mismatch", userLocale));
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("password.complexity.invalid", userLocale));
        }
    }

    /**
     * Sends a non-blocking notification to the user that their password was changed successfully.
     * Uses the user's preferred language (with request locale fallback) for subject and body;
     * failures are logged only so a successful password change is not rolled back by email errors.
     *
     * @param user the account whose {@link User#getEmail()} receives the message
     */
    private void sendPasswordChangeEmail(User user) {
        try {
            Locale userLocale = resolvePreferredLocale(user, LocaleContextHolder.getLocale());

            String subject = messageUtil.getMessage("email.password.change.subject", userLocale);
            String bodyText = messageUtil.getMessage(
                    "email.password.change.body",
                    userLocale,
                    formatGreetingNameForLocale(user.getFirstName(), user.getLastName(), userLocale)
            );
            String htmlBody = buildAuthEmailCardHtml(subject, bodyText, null, null);

            emailSender.sendEmail(user.getEmail(), subject, htmlBody);
            log.info("Password change email sent successfully for user id: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send password change email for user id: {}. Error: {}", user.getId(), e.getMessage(), e);
            // Don't throw exception - password change was successful, email is just a notification
        }
    }
    
    private record PreparedEmail(String to, String subject, String htmlBody) {
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailTaskExecutor.execute(task);
                }
            });
        } else {
            emailTaskExecutor.execute(task);
        }
    }

    /**
     * Queues SMTP delivery on {@link #emailTaskExecutor} after the current transaction commits.
     * Failures are logged only so HTTP latency is not tied to mail provider round-trips.
     */
    private void schedulePreparedEmailAfterCommit(PreparedEmail prepared, UUID userId, String logLabel) {
        runAfterCommit(() -> {
            try {
                log.info("Attempting to send {} email for user id: {}", logLabel, userId);
                emailSender.sendEmail(prepared.to(), prepared.subject(), prepared.htmlBody());
                log.info("{} email sent successfully for user id: {}", logLabel, userId);
            } catch (Exception e) {
                log.error("Failed to send {} email async for user id: {}. Error: {}", logLabel, userId, e.getMessage(), e);
            }
        });
    }

    private void schedulePreparedEmailsAfterCommit(List<PreparedEmail> preparedEmails, UUID userId, String logLabel) {
        runAfterCommit(() -> {
            int successCount = 0;
            for (PreparedEmail prepared : preparedEmails) {
                try {
                    emailSender.sendEmail(prepared.to(), prepared.subject(), prepared.htmlBody());
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send {} email async for user id: {} to {}. Error: {}",
                            logLabel, userId, prepared.to(), e.getMessage(), e);
                }
            }
            log.info("Completed async {} emails for user id: {} (sent {}/{})",
                    logLabel, userId, successCount, preparedEmails.size());
        });
    }

    /**
     * Builds localized OTP email content for {@link User#getEmail()}.
     *
     * @throws ResponseStatusException if the user has no email configured
     */
    private PreparedEmail buildOtpEmailForUser(User user, String otp) {
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.error("User email is null or empty. Cannot send OTP email for user: {}", user.getUserCode());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User email is not configured. Cannot send OTP.");
        }

        Locale userLocale = resolvePreferredLocale(user, LocaleContextHolder.getLocale());
        String subject = messageUtil.getMessage("email.otp.subject", userLocale);
        String bodyText = messageUtil.getMessage(
                "email.otp.body",
                userLocale,
                formatGreetingNameForLocale(user.getFirstName(), user.getLastName(), userLocale),
                otp
        );
        String htmlBody = buildAuthEmailCardHtml(subject, bodyText, otp, null);
        return new PreparedEmail(user.getEmail().trim(), subject, htmlBody);
    }

    /**
     * Sends a non-blocking confirmation that a password reset completed and the account has a new password.
     * Localizes subject and body via {@link #resolvePreferredLocale(User, Locale)}; send failures are
     * logged only and do not affect the already successful reset.
     *
     * @param user the account whose {@link User#getEmail()} receives the message
     */
    private void sendPasswordResetEmail(User user) {
        try {
            Locale userLocale = resolvePreferredLocale(user, LocaleContextHolder.getLocale());

            String subject = messageUtil.getMessage("email.password.reset.subject", userLocale);
            String bodyText = messageUtil.getMessage(
                    "email.password.reset.body",
                    userLocale,
                    formatGreetingNameForLocale(user.getFirstName(), user.getLastName(), userLocale)
            );
            String htmlBody = buildAuthEmailCardHtml(subject, bodyText, null, null);

            emailSender.sendEmail(user.getEmail(), subject, htmlBody);
            log.info("Password reset confirmation email sent successfully for user id: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email for user id: {}. Error: {}", user.getId(), e.getMessage(), e);
            // Don't throw exception - password reset was successful, email is just a notification
        }
    }
    
    /**
     * Builds a password-reset OTP email for a user without an email address (waiter flow),
     * targeting the configured default administrative address.
     *
     * @throws ResponseStatusException if the default outbound address is not configured
     */
    private PreparedEmail buildWaiterPasswordResetOtpEmail(User user, String otp) {
        String recipientEmail = emailProperties.getEmail();
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.error("Default email address is not configured. Cannot send waiter password reset email.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Email configuration error. Please contact administrator.");
        }

        Locale userLocale = resolvePreferredLocale(user, LocaleContextHolder.getLocale());

        String roleName = RoleNames.UNKNOWN;
        if (user.getRoleId() != null) {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            if (role != null) {
                roleName = role.getName();
            }
        }

        String subject = messageUtil.getMessage("email.waiter.password.reset.request.subject", userLocale);
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String fullName = formatFullNameForLocale(firstName, lastName, userLocale);
        String userCode = user.getUserCode() != null ? user.getUserCode() : "";

        String userInfoHtml = buildWaiterInfoCardHtml(
                userLocale,
                fullName,
                userCode,
                roleName
        );

        String bodyText = messageUtil.getMessage(
                "email.waiter.password.reset.request.body",
                userLocale
        );

        String htmlBody = buildAuthEmailCardHtml(subject, bodyText, otp, userInfoHtml);
        return new PreparedEmail(recipientEmail.trim(), subject, htmlBody);
    }
    
    /**
     * Sends the newly generated password for a waiter to all managers of the waiter's
     * restaurant, falling back to a default email if no managers are found.
     *
     * @param user        the waiter user whose password was reset
     * @param newPassword the new plaintext password to communicate
     * @throws ResponseStatusException if no valid recipient can be notified
     */
    private List<PreparedEmail> buildWaiterNewPasswordEmails(User user, String newPassword, Locale userLocale) {
        try {
            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            String userCode = user.getUserCode() != null ? user.getUserCode() : "";

            // Get role name for display in waiter info card (even if role is UNKNOWN)
            String roleName = RoleNames.UNKNOWN;
            if (user.getRoleId() != null) {
                Role role = roleRepository.findById(user.getRoleId()).orElse(null);
                if (role != null) {
                    roleName = role.getName();
                }
            }
            
            // Try to find managers for the waiter's restaurant
            Set<String> recipientEmails = new LinkedHashSet<>();
            
            if (user.getRestaurantId() != null) {
                // Find MANAGER role
                Optional<Role> managerRoleOpt = roleRepository.findByName(RoleNames.MANAGER);
                if (managerRoleOpt.isPresent()) {
                    UUID managerRoleId = managerRoleOpt.get().getId();
                    
                    // Find all managers for the waiter's restaurant
                    List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                            user.getRestaurantId(), managerRoleId);
                    
                    // Collect manager emails (deduplicated)
                    int duplicateCount = 0;
                    for (User manager : managers) {
                        if (manager.getEmail() != null && !manager.getEmail().trim().isEmpty()) {
                            boolean wasNew = recipientEmails.add(manager.getEmail());
                            if (!wasNew) {
                                duplicateCount++;
                                log.debug("Duplicate recipient skipped for manager id: {}", manager.getId());
                            }
                        }
                    }
                    
                    if (duplicateCount > 0) {
                        log.info("Found {} manager(s) for restaurant {} (waiter: {}). Filtered {} duplicate email(s).", 
                                managers.size(), user.getRestaurantId(), user.getUserCode(), duplicateCount);
                    } else {
                        log.info("Found {} manager(s) for restaurant {} (waiter: {})", 
                                managers.size(), user.getRestaurantId(), user.getUserCode());
                    }
                } else {
                    log.warn("MANAGER role not found in database. Will try HQ_ADMIN fallback.");
                }
            } else {
                log.warn("Waiter {} has no restaurantId assigned. Will try HQ_ADMIN fallback.", user.getUserCode());
            }
            
            List<PreparedEmail> preparedEmails = new ArrayList<>();
            
            // If managers found, prepare email to each manager using their preferred language
            if (!recipientEmails.isEmpty()) {
                // Rebuild subject/body per-manager locale
                for (User manager : userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                        user.getRestaurantId(), 
                        roleRepository.findByName(RoleNames.MANAGER).map(Role::getId).orElse(null))) {
                    if (manager.getEmail() == null || manager.getEmail().trim().isEmpty()) {
                        continue;
                    }

                    try {
                        Locale managerLocale = resolvePreferredLocale(manager, userLocale);
                        String managerSubject = messageUtil.getMessage(AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_SUBJECT, managerLocale);

                        String fullName = formatFullNameForLocale(firstName, lastName, managerLocale);
                        String managerUserInfoHtml = buildWaiterInfoCardHtml(
                                managerLocale,
                                fullName,
                                userCode,
                                roleName
                        );

                        String managerBodyText = messageUtil.getMessage(
                                AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_BODY,
                                managerLocale,
                                firstName,
                                lastName,
                                userCode,
                                newPassword);
                        String managerHtmlBody = buildAuthEmailCardHtml(managerSubject, managerBodyText, newPassword, managerUserInfoHtml);
                        preparedEmails.add(new PreparedEmail(manager.getEmail(), managerSubject, managerHtmlBody));
                    } catch (Exception e) {
                        log.error("Failed to prepare waiter new password email for manager {}: {}", manager.getId(), e.getMessage(), e);
                    }
                }
            } else {
                // No managers found: send to all active HQ Admins in their preferred languages
                Optional<Role> hqAdminRoleOpt = roleRepository.findByName(RoleNames.MANAGER.replace(RoleNames.MANAGER, "HQ_ADMIN"));
                if (hqAdminRoleOpt.isEmpty()) {
                    // Fallback if above replacement logic is confusing; explicitly resolve HQ_ADMIN
                    hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                }

                if (hqAdminRoleOpt.isPresent()) {
                    UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                    List<User> hqAdmins = userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(
                            hqAdminRoleId, EntityStatus.ACTIVE);

                    List<User> hqAdminsWithEmail = new java.util.ArrayList<>();
                    for (User hqAdmin : hqAdmins) {
                        if (hqAdmin.getEmail() != null && !hqAdmin.getEmail().trim().isEmpty()) {
                            hqAdminsWithEmail.add(hqAdmin);
                        }
                    }

                    if (!hqAdminsWithEmail.isEmpty()) {
                        log.info("No managers found for waiter {}. Sending new password email to {} HQ_ADMIN user(s).",
                                user.getUserCode(), hqAdminsWithEmail.size());

                        for (User hqAdmin : hqAdminsWithEmail) {
                            try {
                                Locale hqLocale = resolvePreferredLocale(hqAdmin, userLocale);
                                String hqSubject = messageUtil.getMessage(AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_SUBJECT, hqLocale);

                                String fullName = formatFullNameForLocale(firstName, lastName, hqLocale);
                                String hqUserInfoHtml = buildWaiterInfoCardHtml(
                                        hqLocale,
                                        fullName,
                                        userCode,
                                        roleName
                                );

                                String hqBodyText = messageUtil.getMessage(
                                        AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_BODY,
                                        hqLocale,
                                        firstName,
                                        lastName,
                                        userCode,
                                        newPassword);
                                String hqHtmlBody = buildAuthEmailCardHtml(hqSubject, hqBodyText, newPassword, hqUserInfoHtml);
                                preparedEmails.add(new PreparedEmail(hqAdmin.getEmail(), hqSubject, hqHtmlBody));
                            } catch (Exception e) {
                                log.error("Failed to prepare waiter new password email for HQ_ADMIN {}: {}",
                                        hqAdmin.getId(), e.getMessage(), e);
                            }
                        }
                    } else {
                        log.warn("No active HQ_ADMIN users with valid email found for waiter new password email. Will use default email fallback.");
                    }
                } else {
                    log.warn("HQ_ADMIN role not found in database when resolving recipients for waiter new password email. Will use default email fallback.");
                }

                // If still nobody received, fall back to default email
                if (preparedEmails.isEmpty()) {
                    String defaultEmail = emailProperties.getEmail();
                    if (defaultEmail == null || defaultEmail.trim().isEmpty()) {
                        log.error("No managers, HQ_ADMIN users, or default email address configured. Cannot send waiter new password email.");
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Email configuration error. Please contact administrator.");
                    }

                    Locale fallbackLocale = userLocale != null ? userLocale : Locale.ENGLISH;
                    String fallbackSubject = messageUtil.getMessage(AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_SUBJECT, fallbackLocale);
                    String fallbackFullName = formatFullNameForLocale(firstName, lastName, fallbackLocale);
                    String fallbackUserInfoHtml = buildWaiterInfoCardHtml(
                            fallbackLocale,
                            fallbackFullName,
                            userCode,
                            roleName
                    );
                    String fallbackBodyText = messageUtil.getMessage(
                            AuthEmailMessageKeys.FORGOT_PASSWORD_WAITER_NEW_PASSWORD_BODY,
                            fallbackLocale,
                            firstName,
                            lastName,
                            userCode,
                            newPassword);
                    String fallbackHtmlBody = buildAuthEmailCardHtml(
                            fallbackSubject,
                            fallbackBodyText,
                            newPassword,
                            fallbackUserInfoHtml
                    );
                    preparedEmails.add(new PreparedEmail(defaultEmail, fallbackSubject, fallbackHtmlBody));
                }
            }
            
            if (preparedEmails.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to prepare new password email for any recipient. Please try again later.");
            }
            return preparedEmails;
        } catch (ResponseStatusException e) {
            throw e; // Re-throw ResponseStatusException as-is
        } catch (Exception e) {
            log.error("Failed to prepare waiter new password emails. Error: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to prepare new password email. Please try again later.");
        }
    }

    private Locale resolvePreferredLocale(User user, Locale fallbackLocale) {
        if (user != null && user.getLanguageCode() != null && !user.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(user.getLanguageCode().trim());
        }
        return fallbackLocale != null ? fallbackLocale : Locale.ENGLISH;
    }

    /**
     * Builds a self-contained HTML email with a branded card layout: title row, optional waiter-style
     * info fragment, optional centered highlight (OTP or password), and a gray card wrapping the main text.
     * All user-controlled strings are HTML-escaped; newlines in {@code contentText} become {@code <br/>}.
     * When {@code highlightValue} is set and the escaped body contains {@code "expire"} (OTP expiry copy),
     * the substring from the last {@code "expire"} through the following period is wrapped in bold styling.
     *
     * @param title          heading shown at the top of the card (typically the email subject line)
     * @param contentText    main message body as plain text
     * @param highlightValue optional OTP or password to show in a prominent monospace block; {@code null} omits it
     * @param userInfoHtml   optional pre-rendered HTML rows (e.g. from {@link #buildWaiterInfoCardHtml}); {@code null} omits it
     * @return full {@code <!DOCTYPE html>} document suitable for {@code text/html}
     */
    private String buildAuthEmailCardHtml(String title, String contentText, String highlightValue, String userInfoHtml) {
        String safeTitle = escapeHtml(title);
        String safeContent = escapeHtml(contentText).replace("\n", "<br/>");
        String displayContent = safeContent;
        String safeHighlight = highlightValue != null ? escapeHtml(highlightValue) : null;

        String highlightBlock = "";
        if (safeHighlight != null && !safeHighlight.isBlank()) {
            // OTP / password highlight block (no extra label text; uses provided value only)
            highlightBlock = ""
                    + "<tr>"
                    + "<td style=\"padding: 8px 24px 16px 24px;\">"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                    + "<tr>"
                    + "<td align=\"center\" style=\"background:#eef2ff;border:1px solid #c7d2fe;"
                    + "border-radius:10px;padding:16px 14px;font-family:Courier New, Courier, monospace;"
                    + "font-size:22px;font-weight:700;letter-spacing:1px;color:#3730a3;\">"
                    + safeHighlight
                    + AuthEmailHtml.TD_CLOSE
                    + AuthEmailHtml.TR_CLOSE
                    + AuthEmailHtml.TABLE_CLOSE
                    + AuthEmailHtml.TD_CLOSE
                    + AuthEmailHtml.TR_CLOSE;
        }

        // English-only: make the expiry instruction more prominent for OTP emails.
        if (safeHighlight != null
                && safeContent.toLowerCase(Locale.ROOT).contains("expire")) {
            int idx = safeContent.toLowerCase(Locale.ROOT).lastIndexOf("expire");
            if (idx != -1) {
                int endDot = safeContent.indexOf('.', idx);
                if (endDot == -1) {
                    endDot = safeContent.length() - 1;
                }
                int endExclusive = Math.min(endDot + 1, safeContent.length());
                displayContent = safeContent.substring(0, idx)
                        + "<span style=\"font-weight:700;color:#111827;\">"
                        + safeContent.substring(idx, endExclusive)
                        + "</span>"
                        + safeContent.substring(endExclusive);
            }
        }

        return ""
                + "<!DOCTYPE html>"
                + "<html>"
                + "<body style=\"margin:0;padding:16px 0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + "<tr>"
                + "<td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                + "<tr>"
                + "<td style=\"background:#2563eb;height:10px;\">&nbsp;</td>"
                + AuthEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + safeTitle
                + "</div>"
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + (userInfoHtml != null ? userInfoHtml : "")
                + highlightBlock
                + "<tr>"
                + "<td style=\"padding: 0 24px 24px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr>"
                + "<td style=\"padding:14px 16px;font-size:15px;color:#111827;line-height:22px;word-break:break-word;overflow-wrap:anywhere;text-align:left;font-family:Arial,Helvetica,sans-serif;\">"
                + displayContent
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + AuthEmailHtml.TABLE_CLOSE
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + AuthEmailHtml.TABLE_CLOSE
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + AuthEmailHtml.TABLE_CLOSE
                + "</body>"
                + "</html>";
    }

    /**
     * Builds an HTML table fragment (outer {@code <tr>} block) summarizing a waiter for admin-facing emails.
     * Labels reuse registration manager message keys; all displayed values are escaped.
     *
     * @param userLocale locale for the info card title and field labels
     * @param fullName   waiter's formatted full name
     * @param userCode   waiter's user code
     * @param roleName   role display name (may be {@code UNKNOWN} when no role is resolved)
     * @return HTML snippet to pass as {@code userInfoHtml} into {@link #buildAuthEmailCardHtml(String, String, String, String)}
     */
    private String buildWaiterInfoCardHtml(Locale userLocale, String fullName, String userCode, String roleName) {
        String infoTitle = escapeHtml(messageUtil.getMessage("email.waiter.info.title", userLocale));
        // Reuse existing label translations (same values, different context).
        String nameLabel = escapeHtml(messageUtil.getMessage("user.registration.email.manager.name.label", userLocale));
        String userCodeLabel = escapeHtml(messageUtil.getMessage("user.registration.email.manager.usercode.label", userLocale));
        String roleLabel = escapeHtml(messageUtil.getMessage("user.registration.email.manager.role.label", userLocale));

        String safeFullName = escapeHtml(fullName);
        String safeUserCode = escapeHtml(userCode);
        String safeRoleName = escapeHtml(roleName);

        return ""
                + "<tr>"
                + "<td style=\"padding: 0 24px 8px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr>"
                + "<td style=\"padding:12px 16px 0 16px;font-size:12px;color:#6b7280;font-weight:700;\">"
                + infoTitle
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:10px 16px 16px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr>"
                + AuthEmailHtml.TD_LABEL_ROW
                + nameLabel
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TD_VALUE_ROW
                + safeFullName
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + "<tr>"
                + AuthEmailHtml.TD_LABEL_ROW
                + userCodeLabel
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TD_VALUE_ROW
                + safeUserCode
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + "<tr>"
                + AuthEmailHtml.TD_LABEL_ROW
                + roleLabel
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TD_VALUE_ROW
                + safeRoleName
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + AuthEmailHtml.TABLE_CLOSE
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE
                + AuthEmailHtml.TABLE_CLOSE
                + AuthEmailHtml.TD_CLOSE
                + AuthEmailHtml.TR_CLOSE;
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatFullNameForLocale(String firstName, String lastName, Locale locale) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (locale != null && "ja".equalsIgnoreCase(locale.getLanguage())) {
            return (last + " " + first).trim();
        }
        return (first + " " + last).trim();
    }

    private String formatGreetingNameForLocale(String firstName, String lastName, Locale locale) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (locale != null && "ja".equalsIgnoreCase(locale.getLanguage())) {
            return !last.isEmpty() ? last : first;
        }
        return !first.isEmpty() ? first : last;
    }
    
    private UserPasswordAudit buildCompletedPasswordResetAudit(User user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return UserPasswordAudit.builder()
                .user(user)
                .action(AuditAction.RESET_PASSWORD)
                .status(AuditStatus.COMPLETED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Generates a 6-digit numeric one-time password using a secure random source.
     *
     * @return a 6-digit OTP as a string
     */
    private String generateOTP() {
        SecureRandom random = new SecureRandom();
        Integer otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }
    
    /**
     * Generates a random password of 12 characters including at least one uppercase,
     * one lowercase, one digit, and one special character, and shuffles the result.
     *
     * @return a randomly generated password string
     */
    private String generateNewPassword() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%^&+=";
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each required category
        password.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(random.nextInt(26))); // uppercase
        password.append("abcdefghijklmnopqrstuvwxyz".charAt(random.nextInt(26))); // lowercase
        password.append("0123456789".charAt(random.nextInt(10))); // digit
        password.append("@#$%^&+=".charAt(random.nextInt(8))); // special character
        
        // Fill the rest randomly
        for (int i = 4; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        // Shuffle the password
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }
        
        return new String(passwordArray);
    }
    
    /**
     * Send FCM notification to user when password is changed (for all roles)
     */
    /**
     * Sends a password-change notification to the user's device via FCM by publishing
     * a notification event, if a device token is configured for the user.
     *
     * @param user       the user whose password was changed
     * @param userLocale the locale used for localized notification title and body
     */
    private void sendPasswordChangeNotification(User user, Locale userLocale) {
        try {
            // Reload user to ensure we have the latest device token
            User reloadedUser = userRepository.findById(user.getId()).orElse(null);
            if (reloadedUser == null) {
                log.warn("Cannot send password change notification: user {} not found after reload", user.getId());
                return;
            }
            
            String deviceToken = reloadedUser.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.debug("User {} has no device token, skipping password change notification", user.getId());
                return;
            }
            
            // Get user role name for logging
            String roleName = RoleNames.UNKNOWN;
            if (reloadedUser.getRoleId() != null) {
                Role role = roleRepository.findById(reloadedUser.getRoleId()).orElse(null);
                if (role != null) {
                    roleName = role.getName();
                }
            }
            
            // Get notification messages
            String title = messageUtil.getMessage("notification.password.updated.title", userLocale);
            String body = messageUtil.getMessage("notification.password.updated.body", userLocale, 
                    reloadedUser.getFirstName() + " " + reloadedUser.getLastName());
            
            // Prepare additional data
            java.util.Map<String, String> additionalData = new java.util.HashMap<>();
            additionalData.put("userId", reloadedUser.getId().toString());
            additionalData.put("userCode", reloadedUser.getUserCode() != null ? reloadedUser.getUserCode() : "");
            additionalData.put("role", roleName);
            
            // Publish notification via RabbitMQ for all roles
            notificationPublisherService.publishNotification(reloadedUser, title, body, NotificationTypes.PASSWORD_UPDATED, additionalData);
            
            log.info("Password change notification sent to user {} (role: {}) via FCM", reloadedUser.getId(), roleName);
            
        } catch (Exception e) {
            log.error("Failed to send password change notification to user {}: {}", 
                    user.getId(), e.getMessage(), e);
            // Don't throw exception - password change was successful, notification is just a bonus
        }
    }
} 