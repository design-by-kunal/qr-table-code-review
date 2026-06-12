package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.PrintRequestCreateRequest;
import com.gulfnet.shared_library.model.request.PrintRequestDecisionRequest;
import com.gulfnet.shared_library.model.response.dto.PrintRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.PrintRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import java.util.UUID;

public interface PrintRequestService {

    ResponseDto<PrintRequestResponse> createPrintRequest(PrintRequestCreateRequest request, String userId, String userRole, String locale);

    ResponseDto<PrintRequestListResponse> getPendingRequestsForCashier(Integer page, Integer size, String cashierId, String cashierRole, String locale);

    ResponseDto<PrintRequestListResponse> getRequestsForRequester(Integer page, Integer size, String requesterId, String locale);

    ResponseDto<PrintRequestResponse> approvePrintRequest(UUID requestId, PrintRequestDecisionRequest decision, String cashierId, String cashierRole, String locale);

    ResponseDto<PrintRequestResponse> declinePrintRequest(UUID requestId, PrintRequestDecisionRequest decision, String cashierId, String cashierRole, String locale);
}


