package com.gulfnet.shared_library.model.request;

import lombok.Data;
import java.util.UUID;

@Data
public class EmailAvailabilityRequest {
    private String email;
    private UUID userId; // Optional: exclude this user when checking email availability (for update scenarios)
}

