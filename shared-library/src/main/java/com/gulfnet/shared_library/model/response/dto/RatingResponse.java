package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponse {

    private UUID id;
    private UUID orderId;
    private Integer experience;
    private Integer food;
    private Integer service;
    private String feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

