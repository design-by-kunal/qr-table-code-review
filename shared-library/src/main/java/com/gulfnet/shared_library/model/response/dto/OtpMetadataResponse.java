package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Metadata returned when an OTP is sent so that clients can
 * display an accurate expiry timer based on backend state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpMetadataResponse {

    /**
     * OTP expiry timestamp as an OffsetDateTime in UTC.
     * Jackson will serialize this as an ISO-8601 string, e.g. "2026-01-29T12:40:00Z".
     */
    private OffsetDateTime expiresAt;
}

