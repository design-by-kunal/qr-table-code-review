package com.gulfnet.usermanagement.util;

import com.gulfnet.shared_library.util.PaymentMethodTranslationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PaymentMethodDisplaySupport {

    private final MessageUtil messageUtil;

    public String toDisplayName(String paymentMethod) {
        return toDisplayName(paymentMethod, LocaleContextHolder.getLocale());
    }

    public String toDisplayName(String paymentMethod, Locale locale) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return paymentMethod;
        }
        String messageKey = PaymentMethodTranslationUtil.messageKeyFor(paymentMethod);
        if (messageKey == null) {
            return paymentMethod;
        }
        Locale targetLocale = locale != null ? locale : Locale.ENGLISH;
        try {
            return messageUtil.getMessage(messageKey, targetLocale);
        } catch (NoSuchMessageException ex) {
            return paymentMethod.trim();
        }
    }
}
