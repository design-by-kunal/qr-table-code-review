package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.gulfnet.shared_library.enums.EntityStatus; // Add this import
import com.gulfnet.shared_library.model.response.dto.MenuItemDto; 


import java.time.LocalDateTime; // Add this import
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubcategoryDto {
    private UUID id;
    private String name;
    private EntityStatus status; // Added
    private LocalDateTime createdAt; // Added
    private String createdBy; // Added (User's full name)
    private LocalDateTime updatedAt; // Added
    private String updatedBy; // Added (User's full name)
    private List<MenuItemDto> items;  // Changed from ItemDto to MenuItemDto
}