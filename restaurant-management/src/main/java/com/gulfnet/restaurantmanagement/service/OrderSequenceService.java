package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.exception.OrderSequenceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for generating unique sequence numbers for order numbers.
 * 
 * This service uses PostgreSQL's atomic INSERT ... ON CONFLICT ... RETURNING
 * to ensure thread-safe sequence generation without requiring advisory locks
 * or retry mechanisms.
 * 
 * The sequence is scoped by:
 * - restaurant_id: Each restaurant has its own sequence
 * - order_type: DINE_IN and TAKEAWAY have separate sequences
 * - effective_date: Sequence resets daily based on operating hours
 */
@Slf4j
@Service
public class OrderSequenceService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Generates the next sequence number for the given restaurant, order type, and date.
     * 
     * This method uses an atomic database operation that:
     * 1. Inserts a new row with current_value=1 if it doesn't exist
     * 2. Increments and returns the current_value if the row exists
     * 3. All in a single atomic operation (thread-safe)
     * 
     * @param restaurantId The restaurant UUID
     * @param orderType The order type (DINE_IN, TAKEAWAY)
     * @param effectiveDate The effective date for sequence reset
     * @return The next sequence number (starts at 1)
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Long getNextSequence(UUID restaurantId, String orderType, LocalDate effectiveDate) {
        log.debug("Generating sequence - restaurantId: {}, orderType: {}, effectiveDate: {}", 
                restaurantId, orderType, effectiveDate);
        
        try {
            // Use SERIALIZABLE isolation level with atomic INSERT ... ON CONFLICT ... RETURNING
            // SERIALIZABLE ensures that in a distributed environment (multiple servers),
            // transactions are serialized, preventing duplicate sequence values
            // The INSERT ... ON CONFLICT is atomic and handles both insert and update cases
            String query = """
                INSERT INTO order_sequence (restaurant_id, order_type, effective_date, current_value)
                VALUES (:restaurantId, :orderType, :effectiveDate, 1)
                ON CONFLICT (restaurant_id, order_type, effective_date)
                DO UPDATE SET current_value = order_sequence.current_value + 1
                RETURNING current_value
                """;

            Object result = entityManager.createNativeQuery(query)
                    .setParameter("restaurantId", restaurantId)
                    .setParameter("orderType", orderType)
                    .setParameter("effectiveDate", effectiveDate)
                    .getSingleResult();
            
            Long sequenceValue = ((Number) result).longValue();
            
            log.debug("Generated sequence: {} for restaurantId: {}, orderType: {}, effectiveDate: {}", 
                    sequenceValue, restaurantId, orderType, effectiveDate);
            
            return sequenceValue;
            
        } catch (Exception e) {
            log.error("Failed to generate sequence - restaurantId: {}, orderType: {}, effectiveDate: {}: {}", 
                    restaurantId, orderType, effectiveDate, e.getMessage(), e);
            throw new OrderSequenceException("Failed to generate order sequence: " + e.getMessage(), e);
        }
    }
}
