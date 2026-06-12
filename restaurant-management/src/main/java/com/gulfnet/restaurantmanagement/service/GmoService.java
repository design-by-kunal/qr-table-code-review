package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.model.response.PaymentResponse;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public interface GmoService {

    /**
     * Creates an MPM QR code payment via GMO and returns a PaymentResponse
     * with QR data, similar shape to Omise QR payments.
     *
     * @param cashierId         the cashier (userId) initiating the payment
     * @param orderId           the internal order UUID
     * @param orderNumber       the human-readable order number
     * @param amount            payment amount in JPY
     * @param upiType           UPI subtype from request (e.g. paypay, aupay)
     * @param transaction       transaction entity to update (status, GMO IDs)
     * @param locale            current user locale
     * @return PaymentResponse containing QR info
     */
    PaymentResponse createGmoQrPayment(String cashierId,
                                       UUID orderId,
                                       String orderNumber,
                                       BigDecimal amount,
                                       String upiType,
                                       Transaction transaction,
                                       Locale locale);

    /**
     * Starts asynchronous polling for a GMO QR payment linked to the given transaction.
     * Polls for up to maxDurationSeconds and updates DB / sends websockets on status changes.
     *
     * @param transactionId         transaction ID
     * @param locale                locale
     * @param maxDurationSeconds    max polling duration in seconds
     */
    void startAsyncPolling(UUID transactionId, Locale locale, int maxDurationSeconds);

    /**
     * Processes a refund for a GMO UPI payment using the GMO refund API.
     *
     * @param transaction     original transaction (must contain GMO IDs)
     * @param refund          refund entity with amounts
     * @param gmoRefundId     merchant-generated GMO refund slip number (20 digits)
     * @param locale          current user locale
     */
    void processGmoRefund(Transaction transaction, Refund refund, String gmoRefundId, Locale locale);

    /**
     * Same cancel/timeout side-effects as GMO QR polling: keep transaction {@code PENDING}, notify cashier/waiters,
     * broadcast websocket {@code CANCELED}.
     */
    void notifyQrPaymentCanceledOrTimedOut(UUID transactionId, Locale locale, String reason);

    /**
     * LinkType Plus hosted card cancel/failure: mark transaction {@code CANCELED}, clear stored checkout URL,
     * notify cashier/waiters, broadcast websocket {@code CANCELED}.
     */
    void notifyHostedCardPaymentCanceled(UUID transactionId, Locale locale, String reason);
}

