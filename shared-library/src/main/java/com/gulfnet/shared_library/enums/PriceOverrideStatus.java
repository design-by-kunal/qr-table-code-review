package com.gulfnet.shared_library.enums;

public enum PriceOverrideStatus {
    UNSCHEDULED,  // No schedule set or schedule completed
    SCHEDULED,    // Schedule is set, waiting for activation
    LIVE          // Currently active and applying
}

