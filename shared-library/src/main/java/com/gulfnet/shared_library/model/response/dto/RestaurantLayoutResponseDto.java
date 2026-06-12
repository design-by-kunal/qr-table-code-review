package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantLayoutResponseDto {
    private UUID id; 
    private UUID templateId;
    private String templateName;
    private List<RestaurantSectionResponse> sections;
}
