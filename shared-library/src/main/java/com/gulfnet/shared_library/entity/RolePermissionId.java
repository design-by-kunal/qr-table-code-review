package com.gulfnet.shared_library.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId implements Serializable {
    private UUID role;
    private UUID permission;
}
