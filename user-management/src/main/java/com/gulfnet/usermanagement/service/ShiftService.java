package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.model.request.ShiftRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ShiftDataResponse;
import com.gulfnet.shared_library.model.response.dto.ShiftListResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface ShiftService {
    ResponseDto<ShiftDataResponse> createShift(ShiftRequest request);
    ResponseDto<ShiftListResponse> getAllShifts(Integer page, Integer size, String status, String search,
                                                String sortBy, String direction,
                                                Boolean isDeleted, String locale);
    ResponseDto<ShiftDataResponse> getShiftById(UUID shiftId, String locale);
    ResponseDto<ShiftDataResponse> updateShift(UUID shiftId, ShiftRequest request);
    ResponseDto<Void> deleteShift(UUID shiftId, String userId);
    ResponseDto<Void> restoreShifts(List<UUID> ids, String userId);
}
