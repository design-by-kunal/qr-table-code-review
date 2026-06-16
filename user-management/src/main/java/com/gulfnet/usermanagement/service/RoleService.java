package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RoleListResponse;
import com.gulfnet.shared_library.model.response.dto.RoleResponse;
import java.util.List;

public interface RoleService {

    ResponseDto<RoleListResponse> getAllRoles();
} 