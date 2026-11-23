package com.govilink.order;

import com.govilink.identity.AppUser;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    /*
     * Supplied request UUID is also the immutable receipt ID.
     * Reusing it with the same payload cannot collect stock twice.
     */
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private ManagedOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private OrderReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false, updatable = false)
    private AppUser receivedBy;

    @Column(
            name = "quantity_kg",
            nullable = false,
            updatable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityKg;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(nullable = false, updatable = false, length = 500)
    private String reference;

    @Column(name = "requested_version", nullable = false, updatable = false)
    private long requestedVersion;

    protected GoodsReceipt() {
    }

    GoodsReceipt(
            UUID id,
            ManagedOrder order,
            OrderReservation reservation,
            AppUser receivedBy,
            BigDecimal quantityKg,
            Instant receivedAt,
            Instant recordedAt,
            String reference,
            long requestedVersion
    ) {
        this.id = id;
        this.order = order;
        this.reservation = reservation;
        this.receivedBy = receivedBy;
        this.quantityKg = quantityKg;
        this.receivedAt = receivedAt;
        this.recordedAt = recordedAt;
        this.reference = reference;
        this.requestedVersion = requestedVersion;
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public OrderReservation getReservation() {
        return reservation;
    }

    public AppUser getReceivedBy() {
        return receivedBy;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getReference() {
        return reference;
    }

    public long getRequestedVersion() {
        return requestedVersion;
    }
}