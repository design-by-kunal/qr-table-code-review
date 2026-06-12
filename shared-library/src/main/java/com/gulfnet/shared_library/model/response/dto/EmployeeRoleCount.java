package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EmployeeRoleCount {
    private String roleName;
    private Long count;
    private BigDecimal percentage;
}

