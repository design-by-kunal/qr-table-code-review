package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_shift_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserShiftMapping {

    @EmbeddedId
    private UserShiftId id = new UserShiftId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", columnDefinition = "UUID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("shiftId")
    @JoinColumn(name = "shift_id", columnDefinition = "UUID")
    private Shift shift;

    
}
