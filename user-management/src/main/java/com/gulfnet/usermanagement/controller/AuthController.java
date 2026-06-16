package com.gulfnet.usermanagement.controller;

import com.gulfnet.shared_library.model.request.ChangePasswordRequest;
import com.gulfnet.shared_library.model.request.ForgotPasswordRequest;
import com.gulfnet.shared_library.model.request.VerifyOTPRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OtpMetadataResponse;
import com.gulfnet.shared_library.security.EncryptedPayloadDecoder;
import com.gulfnet.usermanagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {
    
    private final AuthService authService;
    private final EncryptedPayloadDecoder encryptedPayloadDecoder;

    /**
     * Changes the password for the currently authenticated user identified by the Bearer token.
     * When {@link ChangePasswordRequest#getPayload()} is set, the body is treated as an RSA-encrypted
     * JSON blob that is decrypted and deserialized into {@link ChangePasswordRequest} before processing.
     *
     * @param authHeader {@code Authorization} header containing a {@code Bearer} access token
     * @param request    plain or RSA-wrapped change-password fields (current and new password, etc.)
     * @return {@link ResponseEntity} with {@link ResponseDto} describing the outcome
     * @throws ResponseStatusException {@code 401} if the header is missing or not Bearer
     */
    @PostMapping("/change-password")
    @Operation(summary = "Change user password", description = "Change password for authenticated user")
    public ResponseEntity<ResponseDto<String>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest request) {
        
        log.info("Received change password request for user");

        request = encryptedPayloadDecoder.decodeIfPresent(
                request, ChangePasswordRequest::getPayload, ChangePasswordRequest.class);

        // Safe token extraction
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        
        String token = authHeader.substring(7);
        ResponseDto<String> response = authService.changePassword(token, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Initiates the password reset flow by validating the provided email or user code
     * and, if valid, triggering OTP generation and delivery to the user.
     * Custom validation ensures that at least one of email or userCode is provided
     * and that email has a valid format when present.
     *
     * @param request the forgot password request containing email and/or user code
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link OtpMetadataResponse}
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset", description = "Send OTP to user's email for password reset")
    public ResponseEntity<ResponseDto<OtpMetadataResponse>> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {
        
        log.info("Received forgot password request");
        
        // Custom validation
        if ((request.getEmail() == null || request.getEmail().trim().isEmpty()) && 
            (request.getUserCode() == null || request.getUserCode().trim().isEmpty())) {
            return ResponseEntity.badRequest()
                    .body(ResponseDto.<OtpMetadataResponse>builder()
                            .message("Either email or userCode must be provided")
                            .data(null)
                            .build());
        }
        
        // Validate email format if provided
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (!request.getEmail().trim().matches(emailRegex)) {
                return ResponseEntity.badRequest()
                        .body(ResponseDto.<OtpMetadataResponse>builder()
                                .message("Invalid email format")
                                .data(null)
                                .build());
            }
        }
        
        ResponseDto<OtpMetadataResponse> response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Completes the forgot-password flow by verifying the OTP and applying the new password.
     * When {@link VerifyOTPRequest#getPayload()} is set, the body is treated as an RSA-encrypted
     * JSON blob that is decrypted and deserialized into {@link VerifyOTPRequest} before processing.
     *
     * @param request plain or RSA-wrapped OTP, identifier (e.g. email), and new password details
     * @return {@link ResponseEntity} with {@link ResponseDto} describing the outcome
     */
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP and reset password", description = "Verify OTP and set new password")
    public ResponseEntity<ResponseDto<String>> verifyOTP(
            @RequestBody VerifyOTPRequest request) {

        request = encryptedPayloadDecoder.decodeIfPresent(
                request, VerifyOTPRequest::getPayload, VerifyOTPRequest.class);

        log.info("Received OTP verification request");
        ResponseDto<String> response = authService.verifyOTP(request);
        return ResponseEntity.ok(response);
    }
} 