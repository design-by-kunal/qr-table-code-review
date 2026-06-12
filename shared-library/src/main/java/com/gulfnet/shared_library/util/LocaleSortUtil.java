package com.gulfnet.shared_library.util;

import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class LocaleSortUtil {

    private LocaleSortUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Constants for method names used in reflection.
     * Following industry standard practice of using constants for method names.
     */
    private static class MethodNames {
        static final String GET_NAME = "getName";
        static final String GET_TRANSLATIONS = "getTranslations";
        static final String GET_TRANSLATION = "getTranslation";
        static final String GET_LOCALIZED_NAME = "getLocalizedName";
        static final String GET_PRICE = "getPrice";
        static final String GET_BASE_PRICE = "getBasePrice";
        static final String GET_CREATED_AT = "getCreatedAt";
        static final String GET_PREFIX = "get";
        
        private MethodNames() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for field names used in sorting.
     * Following industry standard practice of using constants for field names.
     */
    private static class FieldNames {
        static final String NAME = "name";
        static final String PRICE = "price";
        static final String BASE_PRICE = "basePrice";
        static final String CREATED_AT = "createdAt";
        
        private FieldNames() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for locale codes.
     * Following industry standard practice of using constants for locale codes.
     */
    private static class LocaleCodes {
        static final String THAI = "th";
        static final String JAPANESE = "ja";
        
        private LocaleCodes() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Sorts a list of items by given sortBy field and direction with locale-aware sorting for "name".
     * Uses reflection to get getName(), getPrice(), getBasePrice(), or getCreatedAt() methods.
     *
     * Locale is fetched dynamically from LocaleContextHolder.
     *
     * @param items the list of items to sort
     * @param sortBy the field name to sort by ("name", "price", "basePrice", "createdAt", or others)
     * @param direction the sort direction ASC or DESC
     * @param <T> the item type
     */
    public static <T> void sortName(List<T> items, String sortBy, Sort.Direction direction) {
        if (CollectionUtils.isEmpty(items)) return;

        Comparator<T> comparator;

        // Fix the name sorting logic to be more robust
        if (FieldNames.NAME.equalsIgnoreCase(sortBy)) {
            Locale userLocale = LocaleContextHolder.getLocale();
            
            comparator = Comparator.comparing(
                    item -> extractNameFromItem(item),
                    (s1, s2) -> {
                        if (s1 == null) return (s2 == null) ? 0 : 1;
                        if (s2 == null) return -1;
                        
                        Collator collator = switch (userLocale.getLanguage()) {
                            case LocaleCodes.THAI -> Collator.getInstance(new Locale(LocaleCodes.THAI, "TH"));
                            case LocaleCodes.JAPANESE -> Collator.getInstance(new Locale(LocaleCodes.JAPANESE, "JP"));
                            default -> Collator.getInstance(Locale.US);
                        };
                        collator.setStrength(Collator.PRIMARY);
                        return collator.compare(s1, s2);
                    }
            );
        }
         else if (FieldNames.PRICE.equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    item -> {
                        try {
                            Object priceObj = item.getClass().getMethod(MethodNames.GET_PRICE).invoke(item);
                            if (priceObj instanceof BigDecimal) {
                                return (BigDecimal) priceObj;
                            } else if (priceObj instanceof Number) {
                                return BigDecimal.valueOf(((Number) priceObj).doubleValue());
                            }
                            return BigDecimal.ZERO;
                        } catch (Exception e) {
                            return BigDecimal.ZERO;
                        }
                    },
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if (FieldNames.BASE_PRICE.equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    item -> {
                        try {
                            Object basePriceObj = item.getClass().getMethod(MethodNames.GET_BASE_PRICE).invoke(item);
                            if (basePriceObj instanceof BigDecimal) {
                                return (BigDecimal) basePriceObj;
                            } else if (basePriceObj instanceof Number) {
                                return BigDecimal.valueOf(((Number) basePriceObj).doubleValue());
                            }
                            return BigDecimal.ZERO;
                        } catch (Exception e) {
                            return BigDecimal.ZERO;
                        }
                    },
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if (FieldNames.CREATED_AT.equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    item -> {
                        try {
                            Object createdAtObj = item.getClass().getMethod(MethodNames.GET_CREATED_AT).invoke(item);
                            if (createdAtObj instanceof LocalDateTime) {
                                return (LocalDateTime) createdAtObj;
                            }
                            return null;
                        } catch (Exception e) {
                            return null;
                        }
                    },
                    Comparator.nullsLast(LocalDateTime::compareTo)
            );
        } else {
            // Default fallback for other fields - try to get the field as Comparable
            comparator = Comparator.comparing(
                    item -> {
                        try {
                            String methodName = MethodNames.GET_PREFIX + sortBy.substring(0, 1).toUpperCase() + sortBy.substring(1);
                            return (Comparable) item.getClass().getMethod(methodName).invoke(item);
                        } catch (Exception e) {
                            return null;
                        }
                    },
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }

        if (direction == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }

        items.sort(comparator);
    }

    /**
     * Extracts the name from an item using multiple fallback strategies.
     * 
     * @param item The item to extract name from
     * @return The extracted name as a string, or empty string if not found
     */
    private static <T> String extractNameFromItem(T item) {
        try {
            Object nameObj = extractNameFromDirectMethod(item);
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj.toString();
            }
            
            nameObj = extractNameFromTranslations(item);
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj.toString();
            }
            
            nameObj = extractNameFromTranslation(item);
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj.toString();
            }
            
            nameObj = extractNameFromLocalizedName(item);
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj.toString();
            }
            
            nameObj = findNameFromMethods(item);
            return Optional.ofNullable(nameObj).map(Object::toString).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Attempts to extract name using direct getName() method.
     */
    private static <T> Object extractNameFromDirectMethod(T item) {
        try {
            return item.getClass().getMethod(MethodNames.GET_NAME).invoke(item);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to extract name from getTranslations().get(0).
     */
    private static <T> Object extractNameFromTranslations(T item) {
        try {
            var translationsMethod = item.getClass().getMethod(MethodNames.GET_TRANSLATIONS);
            Object translations = translationsMethod.invoke(item);
            if (translations instanceof List<?> list && !list.isEmpty()) {
                Object firstTranslation = list.get(0);
                if (firstTranslation != null) {
                    return extractNameFromTranslationObject(firstTranslation);
                }
            }
        } catch (Exception e) {
            // Fall through to next method
        }
        return null;
    }

    /**
     * Attempts to extract name from a translation object using multiple approaches.
     */
    private static Object extractNameFromTranslationObject(Object translation) {
        // Approach 1: Try getName() method
        try {
            Object nameObj = translation.getClass().getMethod(MethodNames.GET_NAME).invoke(translation);
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj;
            }
        } catch (NoSuchMethodException e1) {
            // Approach 2: Try direct field access
            try {
                var nameField = translation.getClass().getDeclaredField(FieldNames.NAME);
                nameField.setAccessible(true);
                Object nameObj = nameField.get(translation);
                if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                    return nameObj;
                }
            } catch (NoSuchFieldException e2) {
                // Approach 3: Try to find any field that might contain the name
                try {
                    var fields = translation.getClass().getDeclaredFields();
                    for (var field : fields) {
                        if (field.getName().toLowerCase().contains(FieldNames.NAME)) {
                            field.setAccessible(true);
                            Object value = field.get(translation);
                            if (value instanceof String && !((String) value).trim().isEmpty()) {
                                return value;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue to next approach
                }
            } catch (Exception e) {
                // Continue to next approach
            }
        } catch (Exception e) {
            // Continue to next approach
        }
        return null;
    }

    /**
     * Attempts to extract name using getTranslation() (singular) method.
     */
    private static <T> Object extractNameFromTranslation(T item) {
        try {
            var translationMethod = item.getClass().getMethod(MethodNames.GET_TRANSLATION);
            Object translation = translationMethod.invoke(item);
            if (translation != null) {
                return translation.getClass().getMethod(MethodNames.GET_NAME).invoke(translation);
            }
        } catch (Exception e) {
            // Fall through to next method
        }
        return null;
    }

    /**
     * Attempts to extract name using getLocalizedName() method.
     */
    private static <T> Object extractNameFromLocalizedName(T item) {
        try {
            var localizedNameMethod = item.getClass().getMethod(MethodNames.GET_LOCALIZED_NAME);
            return localizedNameMethod.invoke(item);
        } catch (Exception e) {
            // Fall through to next method
        }
        return null;
    }

    /**
     * Last resort: tries to find any method that returns a String and contains "name".
     */
    private static <T> Object findNameFromMethods(T item) {
        try {
            var methods = item.getClass().getMethods();
            for (var method : methods) {
                if (method.getName().toLowerCase().contains(FieldNames.NAME) && 
                    method.getParameterCount() == 0 && 
                    method.getReturnType() == String.class) {
                    try {
                        Object nameObj = method.invoke(item);
                        if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                            return nameObj;
                        }
                    } catch (Exception e) {
                        // Continue to next method
                    }
                }
            }
        } catch (Exception e) {
            // Return null if all methods fail
        }
        return null;
    }
}
