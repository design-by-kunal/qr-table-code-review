package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.OmiseProperties;
import com.gulfnet.restaurantmanagement.service.PaymentCredentialService;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantPaymentAccount;
import com.gulfnet.shared_library.model.dto.PaymentCredentials;
import com.gulfnet.shared_library.repository.RestaurantPaymentAccountRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.service.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCredentialServiceImpl implements PaymentCredentialService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantPaymentAccountRepository restaurantPaymentAccountRepository;
    private final CryptoService cryptoService;
    private final OmiseProperties omiseProperties;

    /**
     * Resolves Omise-style API keys for {@code paymentType} (e.g. {@code paypay}, {@code promptpay}, {@code paynow}, compared
     * case-insensitively after trim). When {@link Restaurant#getHasOwnPaymentAccount()} is {@code false},
     * or the restaurant has no active non-deleted {@link RestaurantPaymentAccount} for that type, returns
     * {@link #getChainLevelCredentials(String)}. Otherwise decrypts {@link RestaurantPaymentAccount}
     * encrypted keys via {@link CryptoService} and returns them with {@link PaymentCredentials#isRestaurantSpecific()}
     * set to {@code true}.
     *
     * @param restaurantId restaurant whose payment routing applies
     * @param paymentType  provider/method discriminator passed to account lookup and chain fallback
     * @return public and secret key material plus whether they are restaurant-specific
     * @throws IllegalArgumentException if the restaurant does not exist, or if chain fallback is invoked for an
     *         unsupported {@code paymentType}
     * @throws IllegalStateException    if chain-level keys are missing for the requested type
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentCredentials getPaymentCredentials(UUID restaurantId, String paymentType) {
        // Step 1: Get restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found with id: " + restaurantId));

        // Step 2: Check hasOwnPaymentAccount flag
        if (Boolean.FALSE.equals(restaurant.getHasOwnPaymentAccount())) {
            log.info("Restaurant {} does not have own payment account. Using chain-level credentials for payment type: {}",
                    restaurantId, paymentType);
            return getChainLevelCredentials(paymentType);
        }

        // Step 3: Restaurant has own payment account - find non-deleted account for this payment type
        String normalizedPaymentType = paymentType != null ? paymentType.trim().toLowerCase() : null;
        Optional<RestaurantPaymentAccount> account = restaurantPaymentAccountRepository
                .findByRestaurant_IdAndPaymentTypeIgnoreCaseAndIsDeletedFalse(restaurantId, normalizedPaymentType);

        // Step 4: If found, decrypt and return restaurant credentials; otherwise use chain-level
        if (account.isPresent()) {
            RestaurantPaymentAccount paymentAccount = account.get();
            String decryptedPublicKey = cryptoService.decrypt(paymentAccount.getEncryptedPublicKey());
            String decryptedSecretKey = cryptoService.decrypt(paymentAccount.getEncryptedSecretKey());

            log.info("Using restaurant-specific credentials for restaurantId={}, paymentType={}, accountId={}",
                    restaurantId, normalizedPaymentType, paymentAccount.getId());

            return PaymentCredentials.builder()
                    .publicKey(decryptedPublicKey)
                    .secretKey(decryptedSecretKey)
                    .isRestaurantSpecific(true)
                    .build();
        } else {
            log.info("Restaurant {} has own payment account but no active {} account found. Using chain-level credentials.",
                    restaurantId, normalizedPaymentType);
            return getChainLevelCredentials(paymentType);
        }
    }

    /**
     * Gets chain-level Omise credentials from environment configuration.
     *
     * @param paymentType the payment type (e.g., "paypay", "promptpay")
     * @return PaymentCredentials with chain-level keys
     */
    private PaymentCredentials getChainLevelCredentials(String paymentType) {
        String normalizedType = paymentType != null ? paymentType.trim().toLowerCase() : null;
        String publicKey;
        String secretKey;

        if ("paypay".equals(normalizedType)) {
            publicKey = omiseProperties.getPaypayPublicKey();
            secretKey = omiseProperties.getPaypaySecretKey();
        } else if ("promptpay".equals(normalizedType)) {
            publicKey = omiseProperties.getPromptpayPublicKey();
            secretKey = omiseProperties.getPromptpaySecretKey();
        } else if ("paynow".equals(normalizedType)) {
            publicKey = omiseProperties.getPaynowPublicKey();
            secretKey = omiseProperties.getPaynowSecretKey();
        } else {
            throw new IllegalArgumentException("Unsupported payment type: " + paymentType);
        }

        if (publicKey == null || publicKey.trim().isEmpty() || secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("Chain-level Omise credentials not configured for payment type: " + paymentType);
        }

        return PaymentCredentials.builder()
                .publicKey(publicKey)
                .secretKey(secretKey)
                .isRestaurantSpecific(false)
                .build();
    }
}
