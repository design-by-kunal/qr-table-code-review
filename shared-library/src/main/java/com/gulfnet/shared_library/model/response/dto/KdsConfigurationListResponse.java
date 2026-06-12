package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class KdsConfigurationListResponse {
    private List<KdsConfigurationResponse> configurations;
    private Long count;
    private Long total;
}

