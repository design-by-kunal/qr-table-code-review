package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.RestaurantPaymentAccountService;
import com.gulfnet.restaurantmanagement.service.OmiseService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantPaymentAccount;
import com.gulfnet.shared_library.model.request.RestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPaymentAccountRequest;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountListResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantPaymentAccountViewResponse;
import com.gulfnet.shared_library.repository.RestaurantPaymentAccountRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.service.CryptoService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantPaymentAccountServiceImpl implements RestaurantPaymentAccountService {

    private static final String MSG_RESTAURANT_PAYMENT_ACCOUNT_NOT_FOUND = "restaurant.payment.account.not.found";
    private static final String MSG_RESTAURANT_PAYMENT_ACCOUNT_PUBLIC_KEY_REQUIRED = "restaurant.payment.account.publicKey.required";
    private static final String MSG_RESTAURANT_PAYMENT_ACCOUNT_DELETED_ERROR = "restaurant.payment.account.deleted.error";
    private static final String SORT_FIELD_CREATED_AT = "createdAt";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantPaymentAccountRepository restaurantPaymentAccountRepository;
    private final CryptoService cryptoService;
    private final MessageUtil messageUtil;
    private final OmiseService omiseService;

    /**
     * Creates or updates (upserts) a restaurant payment account for a given payment provider/type.
     * <p>
     * Validates request fields, validates the Omise secret key, encrypts public/secret keys, and then:
     * </p>
     * <ul>
     *   <li>Updates an existing non-deleted account for the same restaurant + payment type, or</li>
     *   <li>Creates a new active account if none exists.</li>
     * </ul>
     * Also marks the restaurant as having its own payment account.
     *
     * @param userId acting user id (string UUID) used for created/updated metadata
     * @param request upsert payload containing restaurant id, payment type, and provider keys
     * @param locale BCP-47 language tag used for localized messages
     * @return response containing the saved account metadata (keys are not returned)
     * @throws ResponseStatusException when validation fails or the Omise key is invalid
     * @throws EntityNotFoundException when the restaurant does not exist or is deleted
     * @throws IllegalArgumentException when {@code userId} is not a valid UUID
     */
    @Override
    @Transactional
    public ResponseDto<RestaurantPaymentAccountResponse> upsertRestaurantPaymentAccount(
            String userId,
            RestaurantPaymentAccountRequest request,
            String locale
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate request
        validateUpsertRequest(request, userLocale);

        // Validate and parse userId - let IllegalArgumentException propagate naturally
        UUID userUuid = UUID.fromString(userId);

        // Validate Omise secret key before encrypting/saving it.
        omiseService.validateOmiseSecretKey(request.getSecretKey(), userLocale);

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(request.getRestaurantId())
                .orElseThrow(() -> new EntityNotFoundException(
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        String normalizedPaymentType = request.getPaymentType().trim().toLowerCase();

        // Encrypt keys with local AES-256 service
        String encryptedPublicKey = cryptoService.encrypt(request.getPublicKey());
        String encryptedSecretKey = cryptoService.encrypt(request.getSecretKey());

        // Find an existing non-deleted account for this restaurant and payment type
        // If none exists (even if deleted ones do), create a new active account.
        RestaurantPaymentAccount account = restaurantPaymentAccountRepository
                .findByRestaurant_IdAndPaymentTypeIgnoreCaseAndIsDeletedFalse(restaurant.getId(), normalizedPaymentType)
                .orElseGet(() -> {
                    log.info("No active payment account found for restaurantId={}, paymentType={}. Creating a new one.",
                            restaurant.getId(), normalizedPaymentType);
                    return RestaurantPaymentAccount.builder()
                            .restaurant(restaurant)
                            .paymentType(normalizedPaymentType)
                            .isDeleted(false)
                            .createdBy(userUuid)
                            .build();
                });

        account.setEncryptedPublicKey(encryptedPublicKey);
        account.setEncryptedSecretKey(encryptedSecretKey);
        account.setPaymentType(normalizedPaymentType);
        account.setIsDeleted(false);
        account.setUpdatedBy(userUuid);

        account = restaurantPaymentAccountRepository.save(account);
        log.info("Saved restaurant payment account. restaurantId={}, paymentType={}",
                restaurant.getId(), normalizedPaymentType);

        // Mark restaurant as having its own payment account
        restaurant.setHasOwnPaymentAccount(Boolean.TRUE);
        restaurant.setUpdatedBy(restaurant.getUpdatedBy()); // keep existing relation if any
        restaurantRepository.save(restaurant);

        RestaurantPaymentAccountResponse response = mapToResponse(account, restaurant);

        return ResponseDto.<RestaurantPaymentAccountResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("restaurant.payment.account.saved.successfully", userLocale))
                .build();
    }

    /**
     * Retrieves a payment account by id and returns decrypted provider keys for viewing.
     *
     * @param accountId payment account id
     * @param locale    BCP-47 language tag used for localized messages
     * @return response containing account details including decrypted keys
     * @throws EntityNotFoundException when the account does not exist
     * @throws ResponseStatusException when the account is marked deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantPaymentAccountViewResponse> getRestaurantPaymentAccountById(
            UUID accountId,
            String locale
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        RestaurantPaymentAccount account = restaurantPaymentAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_NOT_FOUND, userLocale)));

        // Check if account is deleted
        if (Boolean.TRUE.equals(account.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_DELETED_ERROR, userLocale));
        }

        // Decrypt keys
        String decryptedPublicKey = cryptoService.decrypt(account.getEncryptedPublicKey());
        String decryptedSecretKey = cryptoService.decrypt(account.getEncryptedSecretKey());

        RestaurantPaymentAccountViewResponse response = RestaurantPaymentAccountViewResponse.builder()
                .id(account.getId())
                .restaurantId(account.getRestaurant().getId())
                .paymentType(account.getPaymentType())
                .publicKey(decryptedPublicKey)
                .secretKey(decryptedSecretKey)
                .createdAt(account.getCreatedAt() != null ? account.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(account.getUpdatedAt() != null ? account.getUpdatedAt().toLocalDateTime() : null)
                .build();

        return ResponseDto.<RestaurantPaymentAccountViewResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("restaurant.payment.account.view.success", userLocale))
                .build();
    }

    /**
     * Updates the encrypted public/secret keys for an existing payment account (payment type is unchanged).
     * <p>
     * Validates request fields and Omise secret key, then encrypts and persists the new keys.
     * </p>
     *
     * @param accountId account id to update
     * @param userId    acting user id (string UUID) used for updated metadata
     * @param request   update payload containing new public/secret keys
     * @param locale    BCP-47 language tag used for localized messages
     * @return response containing updated account metadata (keys are not returned)
     * @throws ResponseStatusException when validation fails or the account is deleted
     * @throws EntityNotFoundException when the account does not exist
     * @throws IllegalArgumentException when {@code userId} is not a valid UUID
     */
    @Override
    @Transactional
    public ResponseDto<RestaurantPaymentAccountResponse> updateRestaurantPaymentAccount(
            UUID accountId,
            String userId,
            UpdateRestaurantPaymentAccountRequest request,
            String locale
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate request
        validateUpdateRequest(request, userLocale);

        // Validate and parse userId - let IllegalArgumentException propagate naturally
        UUID userUuid = UUID.fromString(userId);

        // Validate Omise secret key before encrypting/saving it.
        omiseService.validateOmiseSecretKey(request.getSecretKey(), userLocale);

        // Validate payment account exists
        RestaurantPaymentAccount account = restaurantPaymentAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_NOT_FOUND, userLocale)));

        // Check if account is deleted
        if (Boolean.TRUE.equals(account.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_DELETED_ERROR, userLocale));
        }

        // Encrypt new keys
        String encryptedPublicKey = cryptoService.encrypt(request.getPublicKey());
        String encryptedSecretKey = cryptoService.encrypt(request.getSecretKey());

        // Update only the keys (payment type remains unchanged)
        account.setEncryptedPublicKey(encryptedPublicKey);
        account.setEncryptedSecretKey(encryptedSecretKey);
        account.setUpdatedBy(userUuid);

        account = restaurantPaymentAccountRepository.save(account);
        log.info("Updated restaurant payment account keys. accountId={}, restaurantId={}, paymentType={}",
                accountId, account.getRestaurant().getId(), account.getPaymentType());

        RestaurantPaymentAccountResponse response = mapToResponse(account, account.getRestaurant());

        return ResponseDto.<RestaurantPaymentAccountResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("restaurant.payment.account.updated.successfully", userLocale))
                .build();
    }

    /**
     * Validates required fields for upserting a restaurant payment account.
     *
     * @param request upsert request (must be non-null)
     * @param locale  locale for localized error messages
     * @throws ResponseStatusException when required fields are missing/blank
     */
    private void validateUpsertRequest(RestaurantPaymentAccountRequest request, Locale locale) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("assign.employees.restaurantId.required", locale));
        }

        if (request.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("assign.employees.restaurantId.required", locale));
        }

        if (request.getPaymentType() == null || request.getPaymentType().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.payment.account.paymentType.required", locale));
        }

        if (request.getPublicKey() == null || request.getPublicKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_PUBLIC_KEY_REQUIRED, locale));
        }

        if (request.getSecretKey() == null || request.getSecretKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.payment.account.secretKey.required", locale));
        }
    }

    /**
     * Validates required fields for updating a restaurant payment account's keys.
     *
     * @param request update request (must be non-null)
     * @param locale  locale for localized error messages
     * @throws ResponseStatusException when required fields are missing/blank
     */
    private void validateUpdateRequest(UpdateRestaurantPaymentAccountRequest request, Locale locale) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_PUBLIC_KEY_REQUIRED, locale));
        }

        if (request.getPublicKey() == null || request.getPublicKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_PUBLIC_KEY_REQUIRED, locale));
        }

        if (request.getSecretKey() == null || request.getSecretKey().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.payment.account.secretKey.required", locale));
        }
    }

    /**
     * Soft-deletes a restaurant payment account by marking {@code isDeleted=true}.
     * <p>
     * If this was the last remaining non-deleted payment account for the restaurant, the restaurant is also updated
     * to reflect {@code hasOwnPaymentAccount=false}.
     * </p>
     *
     * @param accountId account id to delete
     * @param userId    acting user id (string UUID) used for updated metadata
     * @param locale    BCP-47 language tag used for localized messages
     * @return response with a success message
     * @throws EntityNotFoundException when the account does not exist
     * @throws ResponseStatusException when the account is already deleted
     * @throws IllegalArgumentException when {@code userId} is not a valid UUID
     */
    @Override
    @Transactional
    public ResponseDto<String> deleteRestaurantPaymentAccount(
            UUID accountId,
            String userId,
            String locale
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate and parse userId - let IllegalArgumentException propagate naturally
        UUID userUuid = UUID.fromString(userId);

        // Validate payment account exists
        RestaurantPaymentAccount account = restaurantPaymentAccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_NOT_FOUND, userLocale)));

        // Check if already deleted
        if (Boolean.TRUE.equals(account.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_PAYMENT_ACCOUNT_DELETED_ERROR, userLocale));
        }

        // Mark as deleted
        account.setIsDeleted(true);
        account.setUpdatedBy(userUuid);
        restaurantPaymentAccountRepository.save(account);

        log.info("Deleted restaurant payment account. accountId={}, restaurantId={}, paymentType={}",
                accountId, account.getRestaurant().getId(), account.getPaymentType());

        // Check if there are any remaining non-deleted accounts for this restaurant
        List<RestaurantPaymentAccount> remainingAccounts = restaurantPaymentAccountRepository
                .findByRestaurant_IdAndIsDeletedFalse(account.getRestaurant().getId());

        // If no accounts remain, mark restaurant as not having its own payment account
        if (remainingAccounts.isEmpty()) {
            account.getRestaurant().setHasOwnPaymentAccount(Boolean.FALSE);
            restaurantRepository.save(account.getRestaurant());
            log.info("No payment accounts remaining for restaurant. Marked hasOwnPaymentAccount=false. restaurantId={}",
                    account.getRestaurant().getId());
        }

        return ResponseDto.<String>builder()
                .data("Restaurant payment account deleted successfully")
                .message(messageUtil.getMessage("restaurant.payment.account.deleted.successfully", userLocale))
                .build();
    }

    /**
     * Lists active (non-deleted) payment accounts for a restaurant, with optional paging and sorting.
     * <p>
     * Results include decrypted keys for each account. If {@code page/size} are missing or non-positive, the request is
     * treated as unpaged while still applying sorting.
     * </p>
     *
     * @param restaurantId restaurant id to list accounts for
     * @param page         1-based page number (optional)
     * @param size         page size (optional)
     * @param sortBy       sort field (e.g., paymentType/createdAt/updatedAt) (optional)
     * @param direction    sort direction (defaults to DESC when null)
     * @param locale       BCP-47 language tag used for localized messages
     * @return response containing list results and pagination metadata (when paged)
     * @throws EntityNotFoundException when the restaurant does not exist or is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantPaymentAccountListResponseDto> getRestaurantPaymentAccountsByRestaurantId(
            UUID restaurantId,
            Integer page,
            Integer size,
            String sortBy,
            Sort.Direction direction,
            String locale
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate restaurant exists
        restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        // Handle pagination - support both paged and unpaged requests
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        
        // Map sort field to database field name
        String dbSortField = SORT_FIELD_CREATED_AT; // Default sort by createdAt
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            switch (sortField) {
                case "paymenttype":
                    dbSortField = "paymentType";
                    break;
                case "createdat":
                    dbSortField = SORT_FIELD_CREATED_AT;
                    break;
                case "updatedat":
                    dbSortField = "updatedAt";
                    break;
                default:
                    dbSortField = SORT_FIELD_CREATED_AT;
                    break;
            }
        }

        // Determine sort direction - default to DESC when no direction is provided
        Sort.Direction sortDirection = (direction != null) ? direction : Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, dbSortField);

        // Create Pageable
        Pageable pageable;
        Integer metaPage = 1;
        Integer metaSize = 10;
        if (!noPaging) {
            // page/size are non-null and > 0 by definition when noPaging == false
            metaPage = java.util.Objects.requireNonNull(page);
            metaSize = java.util.Objects.requireNonNull(size);
            pageable = PageRequest.of(metaPage - 1, metaSize, sort);
        } else {
            // Even when no pagination, apply sorting by using a large page size
            pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
        }

        // Fetch paginated accounts from database
        Page<RestaurantPaymentAccount> accountsPage = restaurantPaymentAccountRepository
                .findByRestaurant_IdAndIsDeletedFalse(restaurantId, pageable);

        // If no accounts found, return empty list
        if (accountsPage.isEmpty()) {
            RestaurantPaymentAccountListResponseDto emptyResponse = RestaurantPaymentAccountListResponseDto.builder()
                    .restaurantPaymentAccounts(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(null)
                    .errors(null)
                    .build();

            return ResponseDto.<RestaurantPaymentAccountListResponseDto>builder()
                    .data(emptyResponse)
                    .message(messageUtil.getMessage("restaurant.payment.account.list.success", userLocale))
                    .build();
        }

        // Decrypt keys and map to response
        List<RestaurantPaymentAccountListResponse> accountResponses = accountsPage.getContent().stream()
                .map(account -> {
                    String decryptedPublicKey = cryptoService.decrypt(account.getEncryptedPublicKey());
                    String decryptedSecretKey = cryptoService.decrypt(account.getEncryptedSecretKey());

                    return RestaurantPaymentAccountListResponse.builder()
                            .id(account.getId())
                            .paymentType(account.getPaymentType())
                            .publicKey(decryptedPublicKey)
                            .secretKey(decryptedSecretKey)
                            .createdAt(account.getCreatedAt() != null ? account.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(account.getCreatedBy())
                            .updatedAt(account.getUpdatedAt() != null ? account.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(account.getUpdatedBy())
                            .build();
                })
                .toList();

        // Build pagination metadata
        PaginationMetaData metaData = noPaging ? null : PaginationMetaData.builder()
                .page(metaPage)
                .size(metaSize)
                .totalPages(accountsPage.getTotalPages())
                .totalRecords(accountsPage.getTotalElements())
                .build();

        RestaurantPaymentAccountListResponseDto listResponse = RestaurantPaymentAccountListResponseDto.builder()
                .restaurantPaymentAccounts(accountResponses)
                .count((long) accountResponses.size())
                .total(accountsPage.getTotalElements())
                .metaData(metaData)
                .errors(null)
                .build();

        return ResponseDto.<RestaurantPaymentAccountListResponseDto>builder()
                .data(listResponse)
                .message(messageUtil.getMessage("restaurant.payment.account.list.success", userLocale))
                .build();
    }

    private RestaurantPaymentAccountResponse mapToResponse(RestaurantPaymentAccount account, Restaurant restaurant) {
        return RestaurantPaymentAccountResponse.builder()
                .id(account.getId())
                .restaurantId(restaurant.getId())
                .paymentType(account.getPaymentType())
                .restaurantHasOwnPaymentAccount(Boolean.TRUE.equals(restaurant.getHasOwnPaymentAccount()))
                .createdAt(account.getCreatedAt() != null ? account.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(account.getUpdatedAt() != null ? account.getUpdatedAt().toLocalDateTime() : LocalDateTime.now())
                .build();
    }
}

