package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuDetailStructureDto {
    private MenuDto<MenuResponse> menu;
    private MenuCategoryStructureResponse structure;
}
