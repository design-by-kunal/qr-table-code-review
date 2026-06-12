package com.gulfnet.shared_library.enums;

public enum ShiftStatus {
    OPEN,              // Shift is active
    CLOSED,            // Shift closed, no discrepancy (auto-approved)
    PENDING_APPROVAL,  // Shift closed with discrepancy, awaiting manager approval
    APPROVED,          // Shift closed and approved by manager
    REJECTED           // Shift closed but rejected by manager (needs correction)
}

