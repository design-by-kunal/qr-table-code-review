package com.gulfnet.usermanagement.request.details;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.entity.CashDrawer;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.ShiftTranslation;
import com.gulfnet.shared_library.model.response.dto.ComboCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ItemCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.repository.CashDrawerTranslationRepository;
import com.gulfnet.shared_library.repository.ShiftTranslationRepository;
import com.gulfnet.shared_library.util.CashDrawerTranslationUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.usermanagement.config.LocalizationProperties;
import com.gulfnet.usermanagement.request.cancellation.CancellationRequestBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper for request-details assembly. Centralizes a few cross-cutting helpers used by
 * {@link RequestDetailsServiceImpl} without bloating that class further.
 */
@Component
@RequiredArgsConstructor
public class RequestDetailsCollaborator {

    private static final Logger log = LoggerFactory.getLogger(RequestDetailsCollaborator.class);

    private final CancellationRequestBuilderService cancellationRequestBuilderService;
    private final CashDrawerTranslationRepository cashDrawerTranslationRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final LocalizationProperties localizationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getRequestTypeFromData(String requestDataJson) {
        if (requestDataJson == null || requestDataJson.trim().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = objectMapper.readValue(requestDataJson, Map.class);
            Object val = requestData.get("requestType");
            return val instanceof String requestType ? requestType : null;
        } catch (Exception e) {
            log.warn("Error parsing request data to extract requestType: {}", e.getMessage());
            return null;
        }
    }

    public OrderCancellationRequestResponse buildOrderCancellationRequestResponse(Order order, Locale userLocale) {
        return cancellationRequestBuilderService.buildOrderCancellationRequestResponse(order, userLocale);
    }

    public ItemCancellationRequestResponse buildItemCancellationRequestResponse(OrderedItem orderedItem, Locale userLocale) {
        return cancellationRequestBuilderService.buildItemCancellationRequestResponse(orderedItem, userLocale);
    }

    public ComboCancellationRequestResponse buildComboCancellationRequestResponse(OrderedCombo orderedCombo, Locale userLocale) {
        return cancellationRequestBuilderService.buildComboCancellationRequestResponse(orderedCombo, userLocale);
    }

    public String resolveRefundLineDisplayName(String storedName, UUID lineId, boolean orderedComboLine, Locale userLocale) {
        return cancellationRequestBuilderService.resolveRefundLineDisplayName(storedName, lineId, orderedComboLine, userLocale);
    }

    public String resolveCashDrawerNameForUserService(CashDrawer drawer, Locale userLocale) {
        if (drawer == null) {
            return null;
        }
        List<CashDrawerTranslation> list =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(drawer.getId());
        String name = CashDrawerTranslationUtil.resolveName(list, userLocale != null ? userLocale : Locale.ENGLISH);
        return name.isEmpty() ? null : name;
    }

    public String getShiftNameFromShift(Shift shift, String preferredLocale) {
        if (shift == null) {
            return null;
        }

        List<ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shift.getId());
        if (translations == null || translations.isEmpty()) {
            return "";
        }

        Optional<ShiftTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                translations,
                preferredLocale,
                localizationProperties.getLanguages(),
                ShiftTranslation::getLanguageCode
        );

        return translation.map(ShiftTranslation::getName).orElseGet(() -> translations.get(0).getName());
    }
}

