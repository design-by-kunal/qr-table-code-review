package com.gulfnet.restaurantmanagement.config;

import com.gulfnet.restaurantmanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomLocaleResolver implements LocaleResolver {

    private final LocalizationProperties localizationProperties;
    private final UserService userService;

    private static final String DEFAULT_LOCALE = "en";

    public CustomLocaleResolver(LocalizationProperties localizationProperties, UserService userService) {
        this.localizationProperties = localizationProperties;
        this.userService = userService;
    }

    /**
     * Resolves the locale for the current request using a priority-based approach.
     * Priority order: 1) locale header, 2) user's stored language preference, 3) default locale.
     *
     * @param request the HTTP servlet request
     * @return resolved locale based on header, user preference, or default
     */
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // First priority: Check locale header (for immediate language switching)
        String localeHeader = request.getHeader("locale");
        List<String> supportedLocales = localizationProperties.getLanguages();

        if (localeHeader != null && supportedLocales.contains(localeHeader.toLowerCase())) {
            return Locale.forLanguageTag(localeHeader.toLowerCase());
        }

        // Second priority: staff user's stored language preference (requires User-Role; customer JWTs have none)
        String userIdHeader = request.getHeader("User-ID");
        String userRoleHeader = request.getHeader("User-Role");
        if (userIdHeader != null && !userIdHeader.isEmpty()
                && userRoleHeader != null && !userRoleHeader.isEmpty()) {
            try {
                UUID userId = UUID.fromString(userIdHeader);
                Optional<String> userLanguageCode = userService.getUserLanguageCode(userId);
                if (userLanguageCode.isPresent() && supportedLocales.contains(userLanguageCode.get().toLowerCase())) {
                    return Locale.forLanguageTag(userLanguageCode.get().toLowerCase());
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID format, continue to default
            }
        }

        // Third priority: Default locale
        return Locale.forLanguageTag(DEFAULT_LOCALE);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // Not needed for stateless REST
    }
}
