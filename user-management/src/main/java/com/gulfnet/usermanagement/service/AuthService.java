package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.model.request.ChangePasswordRequest;
import com.gulfnet.shared_library.model.request.ForgotPasswordRequest;
import com.gulfnet.shared_library.model.request.VerifyOTPRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OtpMetadataResponse;

public interface AuthService {
    ResponseDto<String> changePassword(String token, ChangePasswordRequest request);
    ResponseDto<OtpMetadataResponse> forgotPassword(ForgotPasswordRequest request);
    ResponseDto<String> verifyOTP(VerifyOTPRequest request);
} 