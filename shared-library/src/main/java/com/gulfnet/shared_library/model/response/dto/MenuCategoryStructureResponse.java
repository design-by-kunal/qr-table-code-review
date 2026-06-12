package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuCategoryStructureResponse {
    private List<CategoryStructureDto> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStructureDto {
        private UUID id;
        private String name;
        private EntityStatus status;
        private Boolean isCombo;
        private List<SubCategoryStructureDto> subcategories;
        private List<MenuItemDto> items;
        private List<Object> combos; // Combos mapped to this category
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryStructureDto {
        private UUID id;
        private String name;
        private EntityStatus status;
        private Boolean isCombo;
        private List<MenuItemDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuItemDto {
        private UUID id;
        private String name;
        private String description;
        private Double basePrice;
        private AlcoholType alcoholType;
        private Boolean outOfStock;
        private EntityStatus status;
        private String imageUrl;
        private ItemOrderType itemOrderType;
    }
}