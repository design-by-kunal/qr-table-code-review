package com.gulfnet.shared_library.util;

import com.gulfnet.shared_library.entity.Restaurant;

/**
 * Canonical address fields for receipt formatting (storage-aligned order:
 * country, state, city, area, address1, address2, location pin).
 */
public record AddressDto(
        String country,
        String state,
        String city,
        String area,
        String address1,
        String address2,
        String locationPin
) {

    /**
     * Builds an {@link AddressDto} from a {@link Restaurant}'s address fields plus an explicit
     * {@code country} label (e.g. resolved display name). All strings are passed through {@link #trimToNull};
     * blank or whitespace-only values become {@code null}. If {@code restaurant} is {@code null}, returns
     * a DTO with every component {@code null}.
     *
     * @param restaurant source for state, city, area, address lines, and postal/location pin; may be {@code null}
     * @param country      country string for the DTO's first component; may be {@code null}
     * @return a new record instance, never {@code null}
     */
    public static AddressDto fromRestaurant(Restaurant restaurant, String country) {
        if (restaurant == null) {
            return new AddressDto(null, null, null, null, null, null, null);
        }
        return new AddressDto(
                trimToNull(country),
                trimToNull(restaurant.getState()),
                trimToNull(restaurant.getCity()),
                trimToNull(restaurant.getArea()),
                trimToNull(restaurant.getAddress1()),
                trimToNull(restaurant.getAddress2()),
                trimToNull(restaurant.getLocationPin())
        );
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
