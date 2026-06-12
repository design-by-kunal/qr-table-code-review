package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.model.request.TableAssignmentRequest;
import com.gulfnet.shared_library.model.request.WaiterTableAssignmentRequest;
import com.gulfnet.shared_library.model.request.TableStatusPayload;
import com.gulfnet.shared_library.model.request.GuestTransferRequest;
import com.gulfnet.shared_library.model.request.TableMoveRequest;
import com.gulfnet.shared_library.model.request.TableSectionRequest;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentResponse;
import com.gulfnet.shared_library.model.response.dto.TableStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentWrapper;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDtoV2;
import com.gulfnet.shared_library.model.response.dto.SessionResponseDto;
import com.gulfnet.shared_library.model.response.dto.GuestTransferResponse;
import com.gulfnet.shared_library.model.response.dto.TableMoveResponse;
import java.util.List;
import java.util.UUID;

public interface TableService {

    ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> assignTableToWaiter(
        TableAssignmentRequest request, String userId, String userRole);

    ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> assignWaitersToTable(
        WaiterTableAssignmentRequest request, String userId, String userRole);

    ResponseDto<TableAssignmentWrapper<TableAssignmentResponse>> unassignTableFromWaiter(
            UUID assignmentId, String userId, String userRole);

    ResponseDto<TableListResponseDto> getTablesByFilters(
            String waiterId,
            String search,
            String status,
            String sectionId,
            String restaurantId,
            Integer page,
            Integer size);

    ResponseDto<TableListResponseDtoV2> getTablesByFiltersV2(
            String waiterId,
            String search,
            String status,
            String sectionId,
            String restaurantId,
            Integer page,
            Integer size);

    ResponseDto<TableStatusResponseWrapper> updateTableStatus(TableStatusPayload payload, String userId, String userRole);

    String getTableQrCodePresignedUrl(UUID tableId);

  String regenerateTableQrCode(UUID tableId, String userId, String userRole);

    ResponseDto<SessionResponseDto> startSession(UUID restaurantId, UUID tableId, QrCodeType qrCodeType, UUID sessionId);

    void validateSession(UUID sessionId, String token);

    // Manager-specific APIs
    ResponseDto<GuestTransferResponse> transferGuests(GuestTransferRequest request, String userId, String userRole);
    
    ResponseDto<TableMoveResponse> moveTables(TableMoveRequest request, String userId, String userRole);

  ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> getActiveWaiterAssignments(Integer page, Integer size, String userId);
  
  ResponseDto<Object> raiseTableSectionRequest(TableSectionRequest request, String userId, String userRole);
  }
