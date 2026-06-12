package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAssignmentListResponse {
    private UUID restaurantId;
    private List<AssignedEmployee> assignedEmployees;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedEmployee {
        private UUID employeeId;
        private String employeeName;
        private String employeeEmail;
        private UUID roleId;
        private String roleName;
        private Integer status;
    }
} 