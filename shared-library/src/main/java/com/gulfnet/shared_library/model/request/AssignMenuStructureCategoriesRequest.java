package com.gulfnet.shared_library.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignMenuStructureCategoriesRequest {
    
    @NotNull(message = "{menu.id.required}")
    private UUID menuId;
    
    @NotNull(message = "{menu.structure.id.required}")
    private UUID menuStructureId;
    
    @NotEmpty(message = "{categories.required}")
    private List<CategoryAssignment> categories;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAssignment {
        @NotNull(message = "{category.id.required}")
        private UUID id;
        
        private EntityStatus status;
        
        private List<SubCategoryAssignment> subcategories;
        
        // Keep field name as `items`; each item can include order type.
        private List<ItemAssignment> items;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryAssignment {
        @NotNull(message = "{subcategory.id.required}")
        private UUID id;
        
        private EntityStatus status;
        
        // Keep field name as `items`; each item can include order type.
        private List<ItemAssignment> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemAssignment {
        @NotNull(message = "{item.id.required}")
        private UUID itemId;

        private ItemOrderType itemOrderType;

        /**
         * Jackson {@link JsonCreator} for a single entry in the {@code items} array: either a plain UUID string
         * (legacy shape, {@link ItemOrderType#BOTH} implied) or an object with {@code itemId} and optional
         * {@code itemOrderType}. Returns {@code null} when {@code node} is null or JSON null.
         *
         * @param node JSON string or object node
         * @return built assignment, or {@code null} for absent/null input
         * @throws IllegalArgumentException if the node is neither textual nor an object, or UUID/type parsing fails
         */
        @JsonCreator
        public static ItemAssignment fromJson(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }

            // Backward compatibility: "items": ["<uuid>"]
            if (node.isTextual()) {
                return ItemAssignment.builder()
                        .itemId(UUID.fromString(node.asText()))
                        .itemOrderType(ItemOrderType.BOTH)
                        .build();
            }

            if (node.isObject()) {
                UUID id = null;
                if (node.hasNonNull("itemId")) {
                    id = UUID.fromString(node.get("itemId").asText());
                }

                ItemOrderType orderType = null;
                if (node.hasNonNull("itemOrderType")) {
                    orderType = ItemOrderType.valueOf(node.get("itemOrderType").asText().toUpperCase());
                }

                return ItemAssignment.builder()
                        .itemId(id)
                        .itemOrderType(orderType)
                        .build();
            }

            throw new IllegalArgumentException("Invalid item format. Expected UUID string or object with itemId.");
        }
    }
}
