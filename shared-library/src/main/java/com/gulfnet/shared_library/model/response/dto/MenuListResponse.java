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

public class MenuListResponse {
    private List<MenuListData> menus;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

