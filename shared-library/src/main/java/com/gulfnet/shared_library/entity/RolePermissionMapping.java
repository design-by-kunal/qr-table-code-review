package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_permission_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(RolePermissionId.class)
public class RolePermissionMapping {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id")
    private Permission permission;
}
