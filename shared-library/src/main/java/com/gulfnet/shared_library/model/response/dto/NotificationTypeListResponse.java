package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationTypeListResponse {
    private List<NotificationTypeResponse> notificationTypes;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

