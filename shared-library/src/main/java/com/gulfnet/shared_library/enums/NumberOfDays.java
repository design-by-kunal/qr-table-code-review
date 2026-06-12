package com.gulfnet.shared_library.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public enum NumberOfDays {
    SEVEN(7),
    FIFTEEN(15),
    THIRTY(30);

    private final int value;
}
