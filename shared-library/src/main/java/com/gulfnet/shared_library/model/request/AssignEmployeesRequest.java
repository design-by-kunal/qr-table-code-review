package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignEmployeesRequest {

    @NotNull(message = "{assign.employees.restaurantId.required}")
    private UUID restaurantId;

    @NotEmpty(message = "{assign.employees.employees.required}")
    @Valid
    private List<EmployeeAssignment> employees;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeAssignment {
        @NotNull(message = "{assign.employees.employeeId.required}")
        private UUID employeeId;

    }
} 