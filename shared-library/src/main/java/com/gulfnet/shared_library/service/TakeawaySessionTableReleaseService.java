package com.gulfnet.shared_library.service;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.Session;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.SessionRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Expires the active session and may set the table back to AVAILABLE for takeaway orders when the order
 * reaches a terminal state (SERVED with completed payment, or CANCELED). Mirrors restaurant-management
 * {@code OrderRecalculationServiceImpl} behavior so the same rules apply from user-management and other services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TakeawaySessionTableReleaseService {

    private final OrderRepository orderRepository;
    private final SessionRepository sessionRepository;
    private final TransactionRepository transactionRepository;
    private final RestaurantTableRepository restaurantTableRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param orderId order to evaluate
     * @param onTableSetAvailable optional callback when the table is set to AVAILABLE (e.g. WebSocket); may be null
     */
    public void maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(
            UUID orderId,
            BiConsumer<Order, UUID> onTableSetAvailable) {
        if (orderId == null) {
            return;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (!isTakeawayServedWithCompletedTransaction(order) && !isTakeawayCanceled(order)) {
            return;
        }
        if (order.getSession() != null) {
            UUID sessionId = order.getSession().getId();
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                if (session.getExpiredAt() == null || session.getExpiredAt().isAfter(now)) {
                    session.setExpiredAt(now);
                    sessionRepository.save(session);
                    log.info("Expired session {} for takeaway order {} (orderStatus={})",
                            sessionId, orderId, order.getOrderStatus());
                }
            }
        }
        maybeSetTableAvailableWhenNoActiveSessionOrdersForTakeaway(orderId, onTableSetAvailable);
    }

    private boolean isTakeawayServedWithCompletedTransaction(Order order) {
        if (order == null || order.getOrderType() != OrderType.TAKEAWAY) {
            return false;
        }
        OrderStatus status = order.getOrderStatus();
        if (status != OrderStatus.SERVED) {
            return false;
        }
        Optional<Transaction> txOpt = transactionRepository.findByOrderId(order.getId());
        return txOpt.isPresent() && txOpt.get().getTransactionStatus() == TransactionStatus.COMPLETED;
    }

    private boolean isTakeawayCanceled(Order order) {
        return order != null
                && order.getOrderType() == OrderType.TAKEAWAY
                && order.getOrderStatus() == OrderStatus.CANCELED;
    }

    /**
     * After a takeaway order is finished or canceled, sets the linked table to AVAILABLE when no other
     * session-active orders remain on that table, then invokes {@code onTableSetAvailable} if supplied.
     *
     * @param orderId              order that triggered the check
     * @param onTableSetAvailable optional callback ({@code order}, {@code tableId}) after the table is updated
     */
    private void maybeSetTableAvailableWhenNoActiveSessionOrdersForTakeaway(
            UUID orderId,
            BiConsumer<Order, UUID> onTableSetAvailable) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (!isTakeawayServedWithCompletedTransaction(order) && !isTakeawayCanceled(order)) {
            return;
        }
        if (order.getRestaurantTable() == null) {
            return;
        }
        UUID tableId = order.getRestaurantTable().getId();

        entityManager.flush();

        List<Order> stillActive = orderRepository.findByTableIdWithActiveSessions(tableId);
        if (!stillActive.isEmpty()) {
            return;
        }

        RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
        if (table == null || table.getTableStatus() != TableStatus.OCCUPIED) {
            return;
        }

        table.setTableStatus(TableStatus.AVAILABLE);
        table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurantTableRepository.save(table);
        log.info("Set table {} to AVAILABLE after takeaway order {} finished (orderStatus={}; no active session orders on table)",
                tableId, orderId, order.getOrderStatus());
        if (onTableSetAvailable != null) {
            try {
                onTableSetAvailable.accept(order, tableId);
            } catch (Exception e) {
                log.error("onTableSetAvailable callback failed for table {} order {}: {}", tableId, orderId, e.getMessage(), e);
            }
        }
    }
}
