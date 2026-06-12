package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    /** {@code 0} = payment initiated by cashier/staff (cash, UPI at counter, etc.). */
    public static final int PAYMENT_INITIATOR_CASHIER = 0;
    /** {@code 1} = payment initiated by customer (self-service hosted card). */
    public static final int PAYMENT_INITIATOR_CUSTOMER = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @Column(name = "transaction_number", unique = true)
    private String transactionNumber; // Will be null until transaction is completed

    @Column(name = "payment_method")
    private String paymentMethod; // CASH, CREDIT_CARD, DEBIT_CARD, UPI

    /**
     * UPI wallet/app from request {@code type} (e.g. paypay, promptpay, paynow);
     * {@code CASH} or {@code CARD} when {@code paymentMethod} is cash or card (no type in request).
     */
    @Column(name = "payment_app", length = 32)
    private String paymentApp;

    @Column(name = "transaction_amount", precision = 10, scale = 2, nullable = true)
    private BigDecimal transactionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    private TransactionStatus transactionStatus; // OPEN, PENDING, COMPLETED, REFUNDED, CANCELED

    // UPI fields
    @Column(name = "upi_reference_number")
    private String upiReferenceNumber;

    @Column(name = "customer_upi_id")
    private String customerUpiId;

    @Column(name = "omise_charge_id")
    private String omiseChargeId;


    @Column(name = "gmo_order_reservation_id")
    private String gmoOrderReservationId;

    @Column(name = "gmo_order_id")
    private String gmoOrderId;

    /** GMO LinkType Plus trade id from result notification ({@code AccessID}). */
    @Column(name = "gmo_access_id", length = 64)
    private String gmoAccessId;

    /** GMO LinkType Plus trade password from result notification ({@code AccessPass}); required for {@code AlterTran} refund. */
    @Column(name = "gmo_access_pass", length = 64)
    private String gmoAccessPass;

    /** GMO LinkType Plus hosted checkout URL returned to the customer (card payment). */
    @Column(name = "gmo_hosted_payment_url", columnDefinition = "text")
    private String gmoHostedPaymentUrl;

    /** When {@link #gmoHostedPaymentUrl} was issued (UTC). */
    @Column(name = "gmo_hosted_payment_link_created_at")
    private OffsetDateTime gmoHostedPaymentLinkCreatedAt;

    // Card fields (for both Credit and Debit)
    @Column(name = "card_number_masked")
    private String cardNumberMasked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    /**
     * Who initiated payment: {@link #PAYMENT_INITIATOR_CASHIER} (0) or {@link #PAYMENT_INITIATOR_CUSTOMER} (1).
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "payment_initiator_type")
    private Integer paymentInitiatorType = PAYMENT_INITIATOR_CASHIER;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @Column(name = "receipt_url")
    private String receiptUrl;

    // Request fields (used for cancellation requests only - refund requests are in Refund table)
    // The request data is stored in the request_data JSON
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status")
    private RequestStatus requestStatus = RequestStatus.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_data", columnDefinition = "jsonb")
    private String requestData;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt; // UTC (timestamptz)

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "request_comments", length = 500)
    private String requestComments;

    @OneToOne(mappedBy = "transaction", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Refund refund;

    public String getOmiseChargeId() {
        return omiseChargeId;
    }

    public void setOmiseChargeId(String omiseChargeId) {
        this.omiseChargeId = omiseChargeId;
    }

    public String getGmoOrderReservationId() {
        return gmoOrderReservationId;
    }

    public void setGmoOrderReservationId(String gmoOrderReservationId) {
        this.gmoOrderReservationId = gmoOrderReservationId;
    }

    public String getGmoOrderId() {
        return gmoOrderId;
    }

    public void setGmoOrderId(String gmoOrderId) {
        this.gmoOrderId = gmoOrderId;
    }
}
