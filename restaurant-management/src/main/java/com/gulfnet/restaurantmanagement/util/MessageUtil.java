package com.gulfnet.restaurantmanagement.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MessageUtil {

    private final MessageSource messageSource;

    @Autowired
    public MessageUtil(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Resolve a message using the current locale from {@link LocaleContextHolder}.
     */
    public String getMessage(String key) {
        Locale currentLocale = LocaleContextHolder.getLocale();
        return getMessage(key, currentLocale);
    }

    public String getMessage(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    public String getMessage(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}