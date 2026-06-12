package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.OperatingHourSlot;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantOperatingHours;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.repository.RestaurantOperatingHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves business-day boundaries from restaurant operating hours: latest closing ({@code toTime})
 * plus configurable extend hours from {@code restaurant.chain.operatingHoursExtendHoursAfterClose}.
 * Used for order-number sequence reset and unused-session expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatingHoursCutoffService {

    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final RestaurantOperatingHoursRepository restaurantOperatingHoursRepository;

    public int getExtendHoursAfterClose() {
        if (restaurantChainConfigProperties.getChain() == null) {
            return 1;
        }
        int hours = restaurantChainConfigProperties.getChain().getOperatingHoursExtendHoursAfterClose();
        return hours >= 0 ? hours : 1;
    }

    /**
     * Effective business date for order-number sequencing at {@code instant} (UTC).
     */
    public LocalDate resolveEffectiveBusinessDate(UUID restaurantId, OffsetDateTime instant) {
        LocalDate calendarDate = instant.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        Optional<OffsetDateTime> cutoff = resolveCutoffInstant(restaurantId, calendarDate);
        if (cutoff.isPresent() && instant.isBefore(cutoff.get())) {
            return calendarDate.minusDays(1);
        }
        return calendarDate;
    }

    public LocalDate resolveEffectiveBusinessDate(Restaurant restaurant, OffsetDateTime instant) {
        return resolveEffectiveBusinessDate(restaurant.getId(), instant);
    }

    /**
     * Cutoff instant on {@code calendarDate} (UTC calendar day): latest {@code toTime} + extend hours.
     * Empty operating hours / closed day → start of the next UTC calendar day.
     */
    public Optional<OffsetDateTime> resolveCutoffInstant(UUID restaurantId, LocalDate calendarDate) {
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(calendarDate.getDayOfWeek().name());
        Optional<OffsetTime> latestClose = findLatestClosingTime(restaurantId, dayOfWeek);
        if (latestClose.isEmpty()) {
            return Optional.of(calendarDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        OffsetTime close = latestClose.get();
        ZoneOffset offset = close.getOffset() != null ? close.getOffset() : ZoneOffset.UTC;
        OffsetDateTime closeInstant = OffsetDateTime.of(calendarDate, close.toLocalTime(), offset);
        return Optional.of(closeInstant.plusHours(getExtendHoursAfterClose()));
    }

    public boolean isAtOrAfterCutoff(UUID restaurantId, OffsetDateTime instant) {
        LocalDate calendarDate = instant.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        return resolveCutoffInstant(restaurantId, calendarDate)
                .map(cutoff -> !instant.isBefore(cutoff))
                .orElse(true);
    }

    public Optional<OffsetTime> findLatestClosingTime(UUID restaurantId, DayOfWeek dayOfWeek) {
        Optional<OffsetTime> forDay = findLatestClosingTimeForDayRecord(restaurantId, dayOfWeek);
        if (forDay.isPresent()) {
            return forDay;
        }
        if (dayOfWeek != DayOfWeek.ALL_DAYS) {
            return findLatestClosingTimeForDayRecord(restaurantId, DayOfWeek.ALL_DAYS);
        }
        return Optional.empty();
    }

    private Optional<OffsetTime> findLatestClosingTimeForDayRecord(UUID restaurantId, DayOfWeek dayOfWeek) {
        Optional<RestaurantOperatingHours> operatingHoursOpt =
                restaurantOperatingHoursRepository.findByRestaurant_IdAndDayOfWeekWithSlots(restaurantId, dayOfWeek);
        if (operatingHoursOpt.isEmpty()) {
            return Optional.empty();
        }
        RestaurantOperatingHours operatingHours = operatingHoursOpt.get();
        if (Boolean.TRUE.equals(operatingHours.getIsClosed())) {
            return Optional.empty();
        }
        List<OperatingHourSlot> slots = operatingHours.getSlots();
        if (slots == null) {
            return Optional.empty();
        }
        Hibernate.initialize(slots);
        if (slots.isEmpty()) {
            return Optional.empty();
        }
        OffsetTime latestToTime = null;
        for (OperatingHourSlot slot : slots) {
            Hibernate.initialize(slot);
            OffsetTime toTime = slot.getToTime();
            if (toTime != null && (latestToTime == null || toTime.isAfter(latestToTime))) {
                latestToTime = toTime;
            }
        }
        return Optional.ofNullable(latestToTime);
    }
}
