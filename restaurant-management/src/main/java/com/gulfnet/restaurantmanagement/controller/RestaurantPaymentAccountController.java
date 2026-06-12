package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.RestaurantPaymentAccountService;
import com.gulfnet.shared_library.model.request.RestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountListResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountViewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/restaurant-payment-accounts")
@RequiredArgsConstructor
public class RestaurantPaymentAccountController {

    private final RestaurantPaymentAccountService restaurantPaymentAccountService;

    /**
     * Creates or updates a restaurant-specific payment account (e.g., Omise PayPay/PromptPay keys).
     * HQ can call this endpoint to register keys for a restaurant and payment type.
     *
     * @param request payload containing restaurantId, paymentType, publicKey, and secretKey
     * @param userId  ID of the user (HQ) performing the operation
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing stored payment account metadata
     */
    @PostMapping
    public ResponseEntity<ResponseDto<RestaurantPaymentAccountResponse>> upsertRestaurantPaymentAccount(
            @RequestBody RestaurantPaymentAccountRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        log.info("Received restaurant payment account upsert request for restaurantId={}, paymentType={}",
                request.getRestaurantId(), request.getPaymentType());

        ResponseDto<RestaurantPaymentAccountResponse> response =
                restaurantPaymentAccountService.upsertRestaurantPaymentAccount(userId, request, locale);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a restaurant payment account by ID with decrypted public and secret keys.
     * This endpoint decrypts the stored keys and returns them in plaintext.
     *
     * @param accountId the UUID of the restaurant payment account
     * @param locale    locale code for localized responses (default: "en")
     * @return response containing restaurant payment account details with decrypted keys
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<ResponseDto<RestaurantPaymentAccountViewResponse>> getRestaurantPaymentAccountById(
            @PathVariable UUID accountId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        log.info("Received restaurant payment account view request for accountId={}", accountId);

        ResponseDto<RestaurantPaymentAccountViewResponse> response =
                restaurantPaymentAccountService.getRestaurantPaymentAccountById(accountId, locale);

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the public and secret keys for an existing restaurant payment account.
     * Only the keys are updated; payment type remains unchanged.
     *
     * @param accountId the UUID of the restaurant payment account to update
     * @param request   payload containing new publicKey and secretKey
     * @param userId    ID of the user (HQ) performing the update
     * @param locale    locale code for localized responses (default: "en")
     * @return response containing updated payment account metadata
     */
    @PutMapping("/{accountId}")
    public ResponseEntity<ResponseDto<RestaurantPaymentAccountResponse>> updateRestaurantPaymentAccount(
            @PathVariable UUID accountId,
            @RequestBody UpdateRestaurantPaymentAccountRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        log.info("Received restaurant payment account update request for accountId={}", accountId);

        ResponseDto<RestaurantPaymentAccountResponse> response =
                restaurantPaymentAccountService.updateRestaurantPaymentAccount(accountId, userId, request, locale);

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a restaurant payment account by marking it as deleted (soft delete).
     *
     * @param accountId the UUID of the restaurant payment account to delete
     * @param userId    ID of the user (HQ) performing the deletion
     * @param locale    locale code for localized responses (default: "en")
     * @return response confirming deletion
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<ResponseDto<String>> deleteRestaurantPaymentAccount(
            @PathVariable UUID accountId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        log.info("Received restaurant payment account delete request for accountId={}", accountId);

        ResponseDto<String> response =
                restaurantPaymentAccountService.deleteRestaurantPaymentAccount(accountId, userId, locale);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and sortable list of restaurant payment accounts for a specific restaurant.
     * Returns all non-deleted payment accounts with decrypted keys.
     *
     * @param restaurantId the UUID of the restaurant
     * @param page         page number for pagination (default: 1)
     * @param size         page size for pagination (default: 10)
     * @param sortBy       field to sort by (paymentType, createdAt, updatedAt)
     * @param direction    sort direction (ASC or DESC, default: DESC)
     * @param locale       locale code for localized responses (default: "en")
     * @return response containing paginated list of restaurant payment accounts
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseDto<RestaurantPaymentAccountListResponseDto>> getRestaurantPaymentAccountsByRestaurantId(
            @PathVariable UUID restaurantId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "direction", required = false, defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        log.info("Received restaurant payment account list request for restaurantId={}, page={}, size={}, sortBy={}, direction={}",
                restaurantId, page, size, sortBy, direction);

        ResponseDto<RestaurantPaymentAccountListResponseDto> response =
                restaurantPaymentAccountService.getRestaurantPaymentAccountsByRestaurantId(
                        restaurantId, page, size, sortBy, direction, locale);

        return ResponseEntity.ok(response);
    }
}

