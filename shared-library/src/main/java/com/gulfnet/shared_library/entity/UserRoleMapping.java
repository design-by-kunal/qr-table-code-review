package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_role_mapping")
public class UserRoleMapping {

    @EmbeddedId
    private UserRoleId id = new UserRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", columnDefinition = "UUID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", columnDefinition = "UUID")
    private Role role;

    // Getters and Setters
}
