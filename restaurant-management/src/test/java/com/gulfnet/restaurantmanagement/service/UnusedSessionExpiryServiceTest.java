package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.Session;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnusedSessionExpiryServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private OperatingHoursCutoffService operatingHoursCutoffService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageUtil messageUtil;

    @InjectMocks
    private UnusedSessionExpiryService unusedSessionExpiryService;

    @Test
    void expireUnusedSessionsForRestaurant_setsTableAvailableWhenLastActiveSessionExpires() {
        UUID restaurantId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime issuedAt = now.minusHours(2);

        Session session = Session.builder()
                .id(sessionId)
                .restaurantId(restaurantId)
                .tableId(tableId)
                .issuedAt(issuedAt)
                .build();

        RestaurantTable table = RestaurantTable.builder()
                .id(tableId)
                .tableStatus(TableStatus.OCCUPIED)
                .build();

        when(sessionRepository.findActiveSessionsWithoutOrdersByRestaurantId(restaurantId))
                .thenReturn(List.of(session));
        when(operatingHoursCutoffService.resolveCutoffInstant(eq(restaurantId), any()))
                .thenReturn(Optional.of(now.minusMinutes(1)));
        when(sessionRepository.findByTableIdAndExpiredAtIsNull(tableId)).thenReturn(List.of());
        when(restaurantTableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(messageUtil.getMessage(eq("table.status.updated"), any())).thenReturn("Table status updated");

        int expired = unusedSessionExpiryService.expireUnusedSessionsForRestaurant(restaurantId);

        assertThat(expired).isEqualTo(1);
        ArgumentCaptor<RestaurantTable> tableCaptor = ArgumentCaptor.forClass(RestaurantTable.class);
        verify(restaurantTableRepository).save(tableCaptor.capture());
        assertThat(tableCaptor.getValue().getTableStatus()).isEqualTo(TableStatus.AVAILABLE);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void expireUnusedSessionsForRestaurant_doesNotSetTableAvailableWhenOtherActiveSessionsRemain() {
        UUID restaurantId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Session expiringSession = Session.builder()
                .id(UUID.randomUUID())
                .restaurantId(restaurantId)
                .tableId(tableId)
                .issuedAt(now.minusHours(2))
                .build();
        Session remainingSession = Session.builder()
                .id(UUID.randomUUID())
                .restaurantId(restaurantId)
                .tableId(tableId)
                .issuedAt(now.minusHours(1))
                .build();

        when(sessionRepository.findActiveSessionsWithoutOrdersByRestaurantId(restaurantId))
                .thenReturn(List.of(expiringSession));
        when(operatingHoursCutoffService.resolveCutoffInstant(eq(restaurantId), any()))
                .thenReturn(Optional.of(now.minusMinutes(1)));
        when(sessionRepository.findByTableIdAndExpiredAtIsNull(tableId))
                .thenReturn(List.of(remainingSession));

        int expired = unusedSessionExpiryService.expireUnusedSessionsForRestaurant(restaurantId);

        assertThat(expired).isEqualTo(1);
        verify(restaurantTableRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
