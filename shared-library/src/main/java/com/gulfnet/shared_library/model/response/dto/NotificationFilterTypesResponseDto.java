package com.gulfnet.shared_library.model.response.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationFilterTypesResponseDto {
    private List<NotificationTypeDto> notificationTypes;
    private Long count;
    private Long total;
    
    @Data
    @Builder
    public static class NotificationTypeDto {
        private String id;
        private String name;
    }
}

