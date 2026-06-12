package com.gulfnet.shared_library.model.response.dto;

import lombok.Data;

@Data
public class LoginResponseDto<T> {
    private T loginAudit;
}