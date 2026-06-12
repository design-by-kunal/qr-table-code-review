package com.gulfnet.shared_library.model.response.dto;
import lombok.*;
import org.springframework.http.HttpStatus;
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@RequiredArgsConstructor
public class ErrorDto {
    private String errorCode;
    private String errorMessage;

    public ErrorDto(HttpStatus errorCode, String errorMessage) {
        this.errorCode = errorCode.toString();
        this.errorMessage = errorMessage;
    }
}
