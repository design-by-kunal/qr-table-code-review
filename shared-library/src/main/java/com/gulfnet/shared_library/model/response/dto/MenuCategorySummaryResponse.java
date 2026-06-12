package com.gulfnet.shared_library.model.response.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuCategorySummaryResponse {
    private UUID menuId;
    private String menuName;
    private List<CategorySummary> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private UUID id;
        private String name;
    }
} 