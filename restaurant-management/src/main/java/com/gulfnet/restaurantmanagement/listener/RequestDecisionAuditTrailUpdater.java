package com.gulfnet.restaurantmanagement.listener;

import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.entity.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the native audit_trail update in a small, isolated transaction.
 * Keeping this separate allows Rabbit listeners to avoid holding a DB transaction
 * during the full message handling path (which may include external I/O).
 */
@Slf4j
@Component
public class RequestDecisionAuditTrailUpdater {

    private static final String PARAM_REQUESTED_BY_ID = "requestedById";
    private static final String PARAM_REQUESTED_AT = "requestedAt";
    private static final String PARAM_REVIEWED_BY_ID = "reviewedById";
    private static final String PARAM_REVIEWED_AT = "reviewedAt";
    private static final String PARAM_AUDIT_TRAIL_ID = "auditTrailId";

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAuditTrailWithRequestInfo(AuditTrail auditTrail, Transaction transaction, String actionTag) {
        if (auditTrail == null || auditTrail.getId() == null || transaction == null) {
            return;
        }
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            entityManager.createNativeQuery(
                            "UPDATE audit_trail SET " +
                                    "requested_by = :requestedById, " +
                                    "requested_at = :requestedAt, " +
                                    "reviewed_by = :reviewedById, " +
                                    "reviewed_at = :reviewedAt " +
                                    "WHERE id = :auditTrailId")
                    .setParameter(PARAM_REQUESTED_BY_ID, transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                    .setParameter(PARAM_REQUESTED_AT, transaction.getRequestedAt() != null ? transaction.getRequestedAt() : null)
                    .setParameter(PARAM_REVIEWED_BY_ID, transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                    .setParameter(PARAM_REVIEWED_AT, transaction.getReviewedAt() != null ? transaction.getReviewedAt() : now)
                    .setParameter(PARAM_AUDIT_TRAIL_ID, auditTrail.getId())
                    .executeUpdate();
            entityManager.flush();
            log.debug("Updated audit trail {} with request/review info ({})", auditTrail.getLogNumber(), actionTag);
        } catch (Exception e) {
            log.error("Failed to update audit trail with request/review info for {}: {}", actionTag, e.getMessage(), e);
            throw e;
        }
    }
}

