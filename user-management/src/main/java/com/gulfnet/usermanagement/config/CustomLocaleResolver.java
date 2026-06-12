package com.gulfnet.usermanagement.config;

import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomLocaleResolver implements LocaleResolver {

    private final LocalizationProperties localizationProperties;
    private final UserRepository userRepository;

    private static final String DEFAULT_LOCALE = "en";

    public CustomLocaleResolver(LocalizationProperties localizationProperties, UserRepository userRepository) {
        this.localizationProperties = localizationProperties;
        this.userRepository = userRepository;
    }

    /**
     * Resolves the {@link Locale} for the current request based on a priority order:
     * 1) explicit {@code locale} header (for immediate language switching),
     * 2) stored user language preference (from {@code User-ID} header),
     * 3) default locale (English) if none of the above are available or valid.
     *
     * @param request the current HTTP request used to inspect headers
     * @return the resolved {@link Locale} to use for this request
     */
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // First priority: Check locale header (for immediate language switching)
        String localeHeader = request.getHeader("locale");
        List<String> supportedLocales = localizationProperties.getLanguages();

        if (localeHeader != null && supportedLocales.contains(localeHeader.toLowerCase())) {
            return Locale.forLanguageTag(localeHeader.toLowerCase());
        }

        // Second priority: Check user's stored language preference
        String userIdHeader = request.getHeader("User-ID");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                UUID userId = UUID.fromString(userIdHeader);
                Optional<String> userLanguageCode = getUserLanguageCode(userId);
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

    /**
     * Cached method to get user language code to avoid repeated database hits
     * Cache TTL: 5 minutes (300 seconds) - reasonable balance between performance and data freshness
     */
    @Cacheable(value = "userLanguagePreferences", key = "#userId", unless = "#result.isEmpty()")
    public Optional<String> getUserLanguageCode(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        return userOpt.map(User::getLanguageCode);
    }
}
