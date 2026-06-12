package com.gulfnet.shared_library.model.response.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class MenuResponse {

    private UUID id;
    private UUID menuMasterId;       // Common ID for multiple versions of the same menu
    private Double version;          // null if draft
    private String status;            // Now shows mapping status: "LIVE", "SCHEDULED", "UNSCHEDULED"
    private List<MenuTranslationDto> translations;
    private List<CategoryResponse> categories; // Ensure this field is here
    private UUID menuStructureId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long restaurantGroupCount;
    private Long restaurantCount;
    private Boolean isEditable;
}