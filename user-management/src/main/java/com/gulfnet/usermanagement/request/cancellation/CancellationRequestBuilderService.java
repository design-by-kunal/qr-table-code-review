package com.gulfnet.usermanagement.request.cancellation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.model.response.dto.ComboCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ItemCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestResponse;
import com.gulfnet.shared_library.repository.ComboTranslationRepository;
import com.gulfnet.shared_library.repository.ItemTranslationRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.usermanagement.config.LocalizationProperties;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class CancellationRequestBuilderService {

    private static final Logger log = LoggerFactory.getLogger(CancellationRequestBuilderService.class);

    private static final String FALLBACK_LANGUAGE_EN = "en";

    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final ItemTranslationRepository itemTranslationRepository;
    private final ComboTranslationRepository comboTranslationRepository;
    private final RoleRepository roleRepository;
    private final TransactionRepository transactionRepository;
    private final LocalizationProperties localizationProperties;
    private final AWSService awsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Runs outside the caller's transaction so a failed lookup cannot mark the parent read-only
     * transaction rollback-only (which surfaces as "Transaction silently rolled back").
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String resolveRefundLineDisplayName(
            String storedName, UUID orderedLineId, boolean orderedComboLine, Locale userLocale) {
        Locale locale = userLocale != null ? userLocale : Locale.ENGLISH;
        try {
            if (orderedComboLine) {
                String name = resolveOrderedComboLineName(orderedLineId, locale);
                if (!isMissingOrPlaceholderRefundName(name)) {
                    return name;
                }
            } else {
                String name = resolveOrderedItemLineName(orderedLineId, locale);
                if (!isMissingOrPlaceholderRefundName(name)) {
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("resolveRefundLineDisplayName failed for orderedLineId={} combo={}: {}",
                    orderedLineId, orderedComboLine, e.getMessage());
        }
        if (!isMissingOrPlaceholderRefundName(storedName)) {
            return storedName;
        }
        return orderedComboLine ? "Combo" : "Item";
    }

    /**
     * ordered_item.id → item.id → item_translation for the request locale (then English).
     */
    private String resolveOrderedItemLineName(UUID orderedItemId, Locale userLocale) {
        return orderedItemRepository.findMenuItemIdByOrderedItemId(orderedItemId)
                .map(itemTranslationRepository::findAllByItemId)
                .map(translations -> pickLocalizedTranslationName(
                        translations,
                        ItemTranslation::getLanguageCode,
                        ItemTranslation::getName,
                        userLocale))
                .orElse(null);
    }

    /**
     * ordered_combo.id → combo.combo_id → combo_translation for the request locale (then English).
     */
    private String resolveOrderedComboLineName(UUID orderedComboId, Locale userLocale) {
        return orderedComboRepository.findMenuComboIdByOrderedComboId(orderedComboId)
                .map(comboTranslationRepository::findByComboComboId)
                .map(translations -> pickLocalizedTranslationName(
                        translations,
                        ComboTranslation::getLanguageCode,
                        ComboTranslation::getName,
                        userLocale))
                .orElse(null);
    }

    private <T> String pickLocalizedTranslationName(
            List<T> translations,
            Function<T, String> languageCodeFn,
            Function<T, String> nameFn,
            Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : FALLBACK_LANGUAGE_EN;
        Optional<String> preferredName = translations.stream()
                .filter(t -> translationLanguageMatches(languageCodeFn.apply(t), preferred))
                .map(nameFn)
                .filter(name -> !isMissingOrPlaceholderRefundName(name))
                .findFirst();
        if (preferredName.isPresent()) {
            return preferredName.get();
        }
        Optional<String> englishName = translations.stream()
                .filter(t -> translationLanguageMatches(languageCodeFn.apply(t), FALLBACK_LANGUAGE_EN))
                .map(nameFn)
                .filter(name -> !isMissingOrPlaceholderRefundName(name))
                .findFirst();
        if (englishName.isPresent()) {
            return englishName.get();
        }
        return translations.stream()
                .map(nameFn)
                .filter(name -> !isMissingOrPlaceholderRefundName(name))
                .findFirst()
                .orElse(null);
    }

    private static boolean translationLanguageMatches(String translationCode, String language) {
        if (translationCode == null || language == null || language.isBlank()) {
            return false;
        }
        String code = translationCode.trim().toLowerCase(Locale.ROOT);
        String lang = language.trim().toLowerCase(Locale.ROOT);
        return code.equals(lang) || code.startsWith(lang) || lang.startsWith(code);
    }

    public ItemCancellationRequestResponse buildItemCancellationRequestResponse(
            OrderedItem orderedItem, Locale userLocale) {
        try {
            if (orderedItem.getItem() != null) {
                Hibernate.initialize(orderedItem.getItem());
                if (orderedItem.getItem().getTranslations() != null) {
                    Hibernate.initialize(orderedItem.getItem().getTranslations());
                }
            }
            if (orderedItem.getOrder() != null) {
                Hibernate.initialize(orderedItem.getOrder());
                if (orderedItem.getOrder().getRestaurant() != null) {
                    Hibernate.initialize(orderedItem.getOrder().getRestaurant());
                    if (orderedItem.getOrder().getRestaurant().getTranslations() != null) {
                        Hibernate.initialize(orderedItem.getOrder().getRestaurant().getTranslations());
                    }
                }
            }
            if (orderedItem.getCancellationRequestedBy() != null) {
                Hibernate.initialize(orderedItem.getCancellationRequestedBy());
            }
            if (orderedItem.getCancellationReviewedBy() != null) {
                Hibernate.initialize(orderedItem.getCancellationReviewedBy());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy-loaded relationships in buildItemCancellationRequestResponse: {}",
                    e.getMessage());
        }

        String itemName = orderedItem.getItem() != null
                ? resolveLocalizedItemName(orderedItem.getItem(), userLocale)
                : "Item";

        String imageUrl = null;
        try {
            if (orderedItem.getItem() != null
                    && orderedItem.getItem().getImageUrl() != null
                    && !orderedItem.getItem().getImageUrl().isEmpty()) {
                imageUrl = awsService.getPreSignedUrl(orderedItem.getItem().getImageUrl());
            }
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for item image: {}", e.getMessage());
        }

        String cancellationReason = null;
        if (orderedItem.getCancellationRequestData() != null) {
            try {
                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto requestDto =
                        objectMapper.readValue(
                                orderedItem.getCancellationRequestData(),
                                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data: {}", e.getMessage());
            }
        }

        String requestedByRole = null;
        if (orderedItem.getCancellationRequestedBy() != null
                && orderedItem.getCancellationRequestedBy().getRoleId() != null) {
            Role role = roleRepository.findById(orderedItem.getCancellationRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }

        String restaurantName = null;
        UUID restaurantId = null;
        if (orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null) {
            restaurantId = orderedItem.getOrder().getRestaurant().getId();
            if (orderedItem.getOrder().getRestaurant().getTranslations() != null
                    && !orderedItem.getOrder().getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = orderedItem.getOrder().getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(orderedItem.getOrder().getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }

        return ItemCancellationRequestResponse.builder()
                .orderedItemId(orderedItem.getId())
                .orderId(orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null)
                .itemName(itemName)
                .imageUrl(imageUrl)
                .quantity(orderedItem.getQuantity())
                .price(orderedItem.getPrice())
                .currentItemStatus(orderedItem.getItemStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(orderedItem.getCancellationRequestStatus())
                .requestedAt(orderedItem.getCancellationRequestedAt() != null
                        ? orderedItem.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(orderedItem.getCancellationRequestedBy() != null
                        ? orderedItem.getCancellationRequestedBy().getId() : null)
                .requestedByName(orderedItem.getCancellationRequestedBy() != null
                        ? orderedItem.getCancellationRequestedBy().getFirstName() + " "
                        + orderedItem.getCancellationRequestedBy().getLastName() : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(orderedItem.getCancellationReviewedAt() != null
                        ? ((OffsetDateTime) orderedItem.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(orderedItem.getCancellationReviewedBy() != null
                        ? orderedItem.getCancellationReviewedBy().getId() : null)
                .reviewedByName(orderedItem.getCancellationReviewedBy() != null
                        ? orderedItem.getCancellationReviewedBy().getFirstName() + " "
                        + orderedItem.getCancellationReviewedBy().getLastName() : null)
                .comments(orderedItem.getCancellationComments())
                .orderedItemModifiers(new ArrayList<>())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }

    public ComboCancellationRequestResponse buildComboCancellationRequestResponse(
            OrderedCombo orderedCombo, Locale userLocale) {
        try {
            if (orderedCombo.getCombo() != null) {
                Hibernate.initialize(orderedCombo.getCombo());
                if (orderedCombo.getCombo().getTranslations() != null) {
                    Hibernate.initialize(orderedCombo.getCombo().getTranslations());
                }
            }
            if (orderedCombo.getOrder() != null) {
                Hibernate.initialize(orderedCombo.getOrder());
                if (orderedCombo.getOrder().getRestaurant() != null) {
                    Hibernate.initialize(orderedCombo.getOrder().getRestaurant());
                    if (orderedCombo.getOrder().getRestaurant().getTranslations() != null) {
                        Hibernate.initialize(orderedCombo.getOrder().getRestaurant().getTranslations());
                    }
                }
            }
            if (orderedCombo.getCancellationRequestedBy() != null) {
                Hibernate.initialize(orderedCombo.getCancellationRequestedBy());
            }
            if (orderedCombo.getCancellationReviewedBy() != null) {
                Hibernate.initialize(orderedCombo.getCancellationReviewedBy());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy-loaded relationships in buildComboCancellationRequestResponse: {}",
                    e.getMessage());
        }

        String comboName = orderedCombo.getCombo() != null
                ? resolveLocalizedComboName(orderedCombo.getCombo(), userLocale)
                : "Combo";

        String imageUrl = null;
        try {
            if (orderedCombo.getCombo() != null
                    && orderedCombo.getCombo().getComboImageUrl() != null
                    && !orderedCombo.getCombo().getComboImageUrl().isEmpty()) {
                imageUrl = awsService.getPreSignedUrl(orderedCombo.getCombo().getComboImageUrl());
            }
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for combo image: {}", e.getMessage());
        }

        String cancellationReason = null;
        if (orderedCombo.getCancellationRequestData() != null) {
            try {
                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto requestDto =
                        objectMapper.readValue(
                                orderedCombo.getCancellationRequestData(),
                                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse combo cancellation request data: {}", e.getMessage());
            }
        }

        String requestedByRole = null;
        if (orderedCombo.getCancellationRequestedBy() != null
                && orderedCombo.getCancellationRequestedBy().getRoleId() != null) {
            Role role = roleRepository.findById(orderedCombo.getCancellationRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }

        String restaurantName = null;
        UUID restaurantId = null;
        if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null) {
            restaurantId = orderedCombo.getOrder().getRestaurant().getId();
            if (orderedCombo.getOrder().getRestaurant().getTranslations() != null
                    && !orderedCombo.getOrder().getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = orderedCombo.getOrder().getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(orderedCombo.getOrder().getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }

        return ComboCancellationRequestResponse.builder()
                .orderedComboId(orderedCombo.getId())
                .orderId(orderedCombo.getOrder() != null ? orderedCombo.getOrder().getId() : null)
                .comboName(comboName)
                .imageUrl(imageUrl)
                .quantity(orderedCombo.getQuantity())
                .price(orderedCombo.getPrice())
                .currentItemStatus(orderedCombo.getItemStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(orderedCombo.getCancellationRequestStatus())
                .requestedAt(orderedCombo.getCancellationRequestedAt() != null
                        ? orderedCombo.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(orderedCombo.getCancellationRequestedBy() != null
                        ? orderedCombo.getCancellationRequestedBy().getId() : null)
                .requestedByName(orderedCombo.getCancellationRequestedBy() != null
                        ? orderedCombo.getCancellationRequestedBy().getFirstName() + " "
                        + orderedCombo.getCancellationRequestedBy().getLastName() : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(orderedCombo.getCancellationReviewedAt() != null
                        ? ((OffsetDateTime) orderedCombo.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(orderedCombo.getCancellationReviewedBy() != null
                        ? orderedCombo.getCancellationReviewedBy().getId() : null)
                .reviewedByName(orderedCombo.getCancellationReviewedBy() != null
                        ? orderedCombo.getCancellationReviewedBy().getFirstName() + " "
                        + orderedCombo.getCancellationReviewedBy().getLastName() : null)
                .comments(orderedCombo.getCancellationComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }

    public TransactionCancellationRequestResponse buildTransactionCancellationRequestResponse(
            Transaction transaction, Locale userLocale) {
        String cancellationReason = null;
        if (transaction.getRequestData() != null) {
            try {
                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto requestDto =
                        objectMapper.readValue(
                                transaction.getRequestData(),
                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto.class);
                if (requestDto != null) {
                    cancellationReason = requestDto.getCancellationReason();
                }
            } catch (JsonProcessingException e) {
                log.warn("Error parsing transaction cancellation request data for transaction {}: {}",
                        transaction.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error parsing transaction cancellation request data for transaction {}: {}",
                        transaction.getId(), e.getMessage(), e);
            }
        }

        String requestedByRole = null;
        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
            Role role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }

        String requestedByName = buildUserName(transaction.getRequestedBy());
        String reviewedByName = buildUserName(transaction.getReviewedBy());

        String restaurantName = null;
        UUID restaurantId = null;
        if (transaction.getRestaurant() != null) {
            restaurantId = transaction.getRestaurant().getId();
            if (transaction.getRestaurant().getTranslations() != null
                    && !transaction.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = transaction.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }

        return TransactionCancellationRequestResponse.builder()
                .transactionId(transaction.getId())
                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                .transactionNumber(transaction.getTransactionNumber())
                .paymentMethod(transaction.getPaymentMethod())
                .transactionAmount(transaction.getOrder() != null && transaction.getOrder().getTotalAmount() != null
                        ? transaction.getOrder().getTotalAmount()
                        : transaction.getTransactionAmount())
                .currentTransactionStatus(transaction.getTransactionStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(transaction.getRequestStatus())
                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction.getReviewedAt() != null
                        ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .reviewedByName(reviewedByName)
                .comments(transaction.getRequestComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }

    public OrderCancellationRequestResponse buildOrderCancellationRequestResponse(Order order, Locale userLocale) {
        try {
            if (order.getRestaurant() != null) {
                Hibernate.initialize(order.getRestaurant());
                if (order.getRestaurant().getTranslations() != null) {
                    Hibernate.initialize(order.getRestaurant().getTranslations());
                }
            }
            if (order.getCancellationRequestedBy() != null) {
                Hibernate.initialize(order.getCancellationRequestedBy());
            }
            if (order.getCancellationReviewedBy() != null) {
                Hibernate.initialize(order.getCancellationReviewedBy());
            }
            if (order.getRestaurantTable() != null) {
                Hibernate.initialize(order.getRestaurantTable());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy relationships for order {}: {}", order.getId(), e.getMessage());
        }

        String cancellationReason = null;
        if (order.getCancellationRequestData() != null) {
            try {
                com.gulfnet.shared_library.model.request.OrderCancellationRequestDto requestDto =
                        objectMapper.readValue(
                                order.getCancellationRequestData(),
                                com.gulfnet.shared_library.model.request.OrderCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data for order {}: {}",
                        order.getId(), e.getMessage());
            }
        }

        String requestedByName = null;
        String requestedByRole = null;
        if (order.getCancellationRequestedBy() != null && order.getCancellationRequestedBy().getRoleId() != null) {
            requestedByName = order.getCancellationRequestedBy().getFirstName() + " "
                    + order.getCancellationRequestedBy().getLastName();
            Optional<Role> requesterRole = roleRepository.findById(order.getCancellationRequestedBy().getRoleId());
            if (requesterRole.isPresent()) {
                requestedByRole = requesterRole.get().getName();
            }
        }

        String reviewedByName = null;
        if (order.getCancellationReviewedBy() != null) {
            reviewedByName = order.getCancellationReviewedBy().getFirstName() + " "
                    + order.getCancellationReviewedBy().getLastName();
        }

        String transactionNumber = null;
        try {
            transactionNumber = transactionRepository.findByOrderId(order.getId())
                    .map(Transaction::getTransactionNumber)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not fetch transactionNumber for order {}: {}", order.getId(), e.getMessage());
        }

        String restaurantName = null;
        UUID restaurantId = null;
        if (order.getRestaurant() != null) {
            restaurantId = order.getRestaurant().getId();
            if (order.getRestaurant().getTranslations() != null
                    && !order.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = order.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(order.getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = order.getRestaurant().getRestaurantCode() != null
                        ? order.getRestaurant().getRestaurantCode() : "Restaurant";
            }
        }

        return OrderCancellationRequestResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .transactionNumber(transactionNumber)
                .currentOrderStatus(order.getOrderStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(order.getCancellationRequestStatus())
                .requestedAt(order.getCancellationRequestedAt() != null
                        ? order.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(order.getCancellationRequestedBy() != null
                        ? order.getCancellationRequestedBy().getId() : null)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(order.getCancellationReviewedAt() != null
                        ? ((OffsetDateTime) order.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(order.getCancellationReviewedBy() != null
                        ? order.getCancellationReviewedBy().getId() : null)
                .reviewedByName(reviewedByName)
                .comments(order.getCancellationComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableName(order.getRestaurantTable() != null
                        ? order.getRestaurantTable().getTableOrder().toString() : null)
                .totalAmount(order.getTotalAmount())
                .build();
    }

    public String resolveLocalizedItemName(Item item, Locale userLocale) {
        if (item == null || item.getTranslations() == null || item.getTranslations().isEmpty()) {
            return "Item";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        item.getTranslations(),
                        preferred,
                        localizationProperties.getLanguages(),
                        ItemTranslation::getLanguageCode,
                        t -> translationDisplayTextForPick(t.getName()))
                .map(ItemTranslation::getName)
                .orElse("Item");
    }

    public String resolveLocalizedComboName(Combo combo, Locale userLocale) {
        if (combo == null || combo.getTranslations() == null || combo.getTranslations().isEmpty()) {
            return "Combo";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        combo.getTranslations(),
                        preferred,
                        localizationProperties.getLanguages(),
                        ComboTranslation::getLanguageCode,
                        t -> translationDisplayTextForPick(t.getName()))
                .map(ComboTranslation::getName)
                .orElse("Combo");
    }

    private static String translationDisplayTextForPick(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if ("NA".equalsIgnoreCase(name.trim())) {
            return null;
        }
        return name;
    }

    private static boolean isMissingOrPlaceholderRefundName(String name) {
        return translationDisplayTextForPick(name) == null;
    }

    private static String buildUserName(com.gulfnet.shared_library.entity.User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}
