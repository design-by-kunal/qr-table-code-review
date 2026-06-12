package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * PayPay return_uri endpoint.
 *
 * Omise redirects the user's browser here after they authorize/cancel the PayPay flow.
 * We include `orderId` in the return_uri so we can map it back to our transaction,
 * which is updated asynchronously by the Omise webhook.
 *
 * Response is plain text:
 * - "success" when transaction is COMPLETED
 * - "failed" when transaction is CANCELED
 * - "pending" otherwise
 */
@Slf4j
@RestController
@RequestMapping("/omise/paypay-return")
@RequiredArgsConstructor
public class OmisePayPayReturnController {

    private final TransactionRepository transactionRepository;

    /**
     * Handles the PayPay return URI callback from Omise after user authorization.
     * Omise redirects the user's browser here after they authorize/cancel the PayPay flow.
     * The orderId is included in the return_uri to map back to the transaction.
     * Returns plain text response: "success" (COMPLETED), "failed" (CANCELED), or "pending" (other statuses).
     *
     * @param orderId the order ID from the return URI query parameter (required)
     * @return plain text response indicating transaction status: "success", "failed", or "pending"
     * @throws ResponseStatusException with BAD_REQUEST if orderId is invalid, or NOT_FOUND if transaction not found
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> paypayReturn(@RequestParam("orderId") String orderId) {
        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid orderId");
        }

        Optional<Transaction> txOpt = transactionRepository.findByOrderId(orderUuid);
        if (txOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }

        TransactionStatus status = txOpt.get().getTransactionStatus();

        if (status == TransactionStatus.COMPLETED) {
            return ResponseEntity.ok("success");
        }
        if (status == TransactionStatus.CANCELED) {
            return ResponseEntity.ok("failed");
        }

        return ResponseEntity.ok("pending");
    }
}

