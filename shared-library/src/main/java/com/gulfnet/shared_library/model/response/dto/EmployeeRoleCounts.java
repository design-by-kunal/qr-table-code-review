package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EmployeeRoleCounts {
    private Long totalActiveEmployees;
    private List<EmployeeRoleCount> roleCounts;
}

