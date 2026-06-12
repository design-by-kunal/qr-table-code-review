package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@Data
public class MenuItemListResponse {
    private List<MenuItemResponse> items;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}
