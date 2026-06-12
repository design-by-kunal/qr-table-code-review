package com.gulfnet.shared_library.model.response.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TableAssignmentResponse {

    private UUID id;

    private UUID restaurantTableId;

    private UUID waiterId;

    private LocalDateTime assignedAt;

    private LocalDateTime unassignedAt;

}
