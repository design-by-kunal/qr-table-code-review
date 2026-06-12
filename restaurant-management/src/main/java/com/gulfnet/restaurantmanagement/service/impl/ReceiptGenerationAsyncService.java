package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.ReceiptService;
import com.gulfnet.restaurantmanagement.service.RefundReceiptService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.entity.RefundItem;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.RefundItemRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptGenerationAsyncService {

    private final TransactionRepository transactionRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final ReceiptService receiptService;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundReceiptService refundReceiptService;

    @Async("notificationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateOrderReceiptAfterPayment(UUID transactionId) {
        try {
            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
            if (transaction == null) {
                log.warn("Async order receipt generation skipped - transaction {} not found", transactionId);
                return;
            }
            if (transaction.getReceiptUrl() != null && !transaction.getReceiptUrl().isBlank()) {
                return;
            }
            Order order = transaction.getOrder();
            if (order == null || order.getRestaurant() == null) {
                log.warn("Async order receipt generation skipped - missing order/restaurant for transaction {}", transactionId);
                return;
            }
            List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
            String receiptUrl = receiptService.generateReceiptPdf(order, transaction, order.getRestaurant(), orderedItems);
            if (receiptUrl != null && !receiptUrl.isBlank()) {
                transaction.setReceiptUrl(receiptUrl);
                transactionRepository.save(transaction);
                log.info("Async order receipt generated for transaction {}", transactionId);
            }
        } catch (Exception e) {
            log.error("Async order receipt generation failed for transaction {}: {}", transactionId, e.getMessage(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateRefundReceiptAfterCompletion(UUID refundId) {
        try {
            Refund refund = refundRepository.findById(refundId).orElse(null);
            if (refund == null) {
                log.warn("Async refund receipt generation skipped - refund {} not found", refundId);
                return;
            }
            if (refund.getReceiptUrl() != null && !refund.getReceiptUrl().isBlank()) {
                return;
            }
            Transaction transaction = refund.getTransaction();
            Order order = transaction != null ? transaction.getOrder() : null;
            if (transaction == null || order == null || transaction.getRestaurant() == null) {
                log.warn("Async refund receipt generation skipped - missing transaction/order/restaurant for refund {}", refundId);
                return;
            }
            List<RefundItem> refundItems = refundItemRepository.findByRefund_Id(refundId);
            String receiptUrl = refundReceiptService.generateRefundReceiptPdf(
                    refund, transaction, order, transaction.getRestaurant(), refundItems);
            if (receiptUrl != null && !receiptUrl.isBlank()) {
                refund.setReceiptUrl(receiptUrl);
                refundRepository.save(refund);
                log.info("Async refund receipt generated for refund {}", refundId);
            }
        } catch (Exception e) {
            log.error("Async refund receipt generation failed for refund {}: {}", refundId, e.getMessage(), e);
        }
    }
}
