package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RoleListResponse;
import com.gulfnet.shared_library.model.response.dto.RoleResponse;
import com.gulfnet.usermanagement.service.RoleService;
import com.gulfnet.usermanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final MessageUtil messageUtil;
    private final EntityManager entityManager;

    /**
     * Retrieves all roles from the repository and maps them to a simple list response
     * with count and total, using the current locale for the success message.
     *
     * @return {@link ResponseDto} containing {@link RoleListResponse} with all roles
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RoleListResponse> getAllRoles() {

        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Fetching all roles");

        @SuppressWarnings("unchecked")
        List<Object[]> roleRows = entityManager.createNativeQuery(
                        "SELECT id, name FROM role")
                .getResultList();

        List<RoleResponse> roleResponses = roleRows.stream()
                .map(role -> RoleResponse.builder()
                        .id((UUID) role[0])
                        .name((String) role[1])
                        .build())
                .collect(Collectors.toList());

        RoleListResponse roleListResponse = RoleListResponse.builder()
                .roles(roleResponses)
                .count((long) roleResponses.size())
                .total((long) roleResponses.size())
                .metaData(null)
                .build();

        return ResponseDto.<RoleListResponse>builder()
                .message(messageUtil.getMessage("role.fetch.success", userLocale))
                .data(roleListResponse)
                .build();
    }
}
