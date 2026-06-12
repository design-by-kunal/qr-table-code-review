package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkAsReadRequest {
    private List<UUID> notificationIds;
}

