package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.RestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountListResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountViewResponse;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public interface RestaurantPaymentAccountService {

    ResponseDto<RestaurantPaymentAccountResponse> upsertRestaurantPaymentAccount(
            String userId,
            RestaurantPaymentAccountRequest request,
            String locale
    );

    ResponseDto<RestaurantPaymentAccountViewResponse> getRestaurantPaymentAccountById(
            UUID accountId,
            String locale
    );

    ResponseDto<RestaurantPaymentAccountResponse> updateRestaurantPaymentAccount(
            UUID accountId,
            String userId,
            UpdateRestaurantPaymentAccountRequest request,
            String locale
    );

    ResponseDto<String> deleteRestaurantPaymentAccount(
            UUID accountId,
            String userId,
            String locale
    );

    ResponseDto<RestaurantPaymentAccountListResponseDto> getRestaurantPaymentAccountsByRestaurantId(
            UUID restaurantId,
            Integer page,
            Integer size,
            String sortBy,
            Sort.Direction direction,
            String locale
    );
}

