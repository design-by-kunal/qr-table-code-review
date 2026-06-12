package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.OperatingHourSlot;
import com.gulfnet.shared_library.entity.RestaurantOperatingHours;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.repository.RestaurantOperatingHoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatingHoursCutoffServiceTest {

    @Mock
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Mock
    private RestaurantOperatingHoursRepository restaurantOperatingHoursRepository;

    @InjectMocks
    private OperatingHoursCutoffService operatingHoursCutoffService;

    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RestaurantChainConfigProperties.RestaurantChainData chain =
                new RestaurantChainConfigProperties.RestaurantChainData();
        chain.setOperatingHoursExtendHoursAfterClose(1);
        when(restaurantChainConfigProperties.getChain()).thenReturn(chain);
    }

    @Test
    void resolveEffectiveBusinessDate_beforeCutoff_usesPreviousCalendarDay() {
        stubClosingTime(DayOfWeek.TUESDAY, OffsetTime.of(22, 0, 0, 0, ZoneOffset.UTC));

        LocalDate tuesday = LocalDate.of(2026, 5, 26);
        OffsetDateTime at1130Pm = tuesday.atTime(22, 30).atOffset(ZoneOffset.UTC);

        LocalDate effective = operatingHoursCutoffService.resolveEffectiveBusinessDate(restaurantId, at1130Pm);

        assertThat(effective).isEqualTo(tuesday.minusDays(1));
    }

    @Test
    void resolveEffectiveBusinessDate_afterCutoff_usesCurrentCalendarDay() {
        stubClosingTime(DayOfWeek.TUESDAY, OffsetTime.of(22, 0, 0, 0, ZoneOffset.UTC));

        LocalDate tuesday = LocalDate.of(2026, 5, 26);
        OffsetDateTime afterCutoff = tuesday.atTime(23, 30).atOffset(ZoneOffset.UTC);

        LocalDate effective = operatingHoursCutoffService.resolveEffectiveBusinessDate(restaurantId, afterCutoff);

        assertThat(effective).isEqualTo(tuesday);
    }

    @Test
    void resolveCutoffInstant_addsConfiguredExtendHours() {
        RestaurantChainConfigProperties.RestaurantChainData chain =
                new RestaurantChainConfigProperties.RestaurantChainData();
        chain.setOperatingHoursExtendHoursAfterClose(2);
        when(restaurantChainConfigProperties.getChain()).thenReturn(chain);
        stubClosingTime(DayOfWeek.TUESDAY, OffsetTime.of(22, 0, 0, 0, ZoneOffset.UTC));

        LocalDate tuesday = LocalDate.of(2026, 5, 26);
        Optional<OffsetDateTime> cutoff =
                operatingHoursCutoffService.resolveCutoffInstant(restaurantId, tuesday);

        assertThat(cutoff).contains(tuesday.atTime(22, 0).atOffset(ZoneOffset.UTC).plusHours(2));
    }

    private void stubClosingTime(DayOfWeek day, OffsetTime toTime) {
        OperatingHourSlot slot = OperatingHourSlot.builder().toTime(toTime).build();
        RestaurantOperatingHours hours = RestaurantOperatingHours.builder()
                .isClosed(false)
                .slots(List.of(slot))
                .build();
        when(restaurantOperatingHoursRepository.findByRestaurant_IdAndDayOfWeekWithSlots(eq(restaurantId), eq(day)))
                .thenReturn(Optional.of(hours));
    }
}
