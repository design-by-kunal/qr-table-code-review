package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Future;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishMenuRequest {
    
    private LocalDateTime schedulePublishTime;
}
