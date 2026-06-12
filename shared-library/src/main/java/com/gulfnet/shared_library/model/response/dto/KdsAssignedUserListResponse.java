package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class KdsAssignedUserListResponse {
    private List<KdsAssignedUserResponse> users;
    private Long count;
    private Long total;
}

