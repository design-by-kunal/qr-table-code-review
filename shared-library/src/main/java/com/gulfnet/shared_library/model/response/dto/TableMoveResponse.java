package com.gulfnet.shared_library.model.response.dto;

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
public class TableMoveResponse {
    
    private List<UUID> movedTableIds;
    /**
     * Present when all moved tables came from one section; null when they came from multiple sections.
     */
    private UUID sourceSectionId;
    /** Distinct source section IDs in first-seen order (always populated; size 1 when sourceSectionId is set). */
    private List<UUID> sourceSectionIds;
    private UUID targetSectionId;
    private String sourceSectionName;
    private String targetSectionName;
    private String reason;
    private String movedBy;
    private LocalDateTime movedAt;
}
