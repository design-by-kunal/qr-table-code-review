package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class KdsAssignedUserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String contactNumber;
    private EntityStatus status;
}

