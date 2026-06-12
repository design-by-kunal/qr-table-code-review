package com.gulfnet.usermanagement.controller;

import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RoleListResponse;
import com.gulfnet.shared_library.model.response.dto.RoleResponse;
import com.gulfnet.usermanagement.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ResponseDto<RoleListResponse>> getAllRoles(
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("Authorization") String authHeader) {

        log.info("Request received to fetch all roles for User-ID: {}, User-Role: {}", userId, userRole);
        return ResponseEntity.ok(roleService.getAllRoles());
    }
}
