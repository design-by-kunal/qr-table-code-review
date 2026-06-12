package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserShiftId implements Serializable {

    private UUID userId;
    private UUID shiftId;
}
