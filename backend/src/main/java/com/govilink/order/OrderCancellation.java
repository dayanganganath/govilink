package com.govilink.order;

import com.govilink.identity.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_cancellations")
public class OrderCancellation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false)
    private ManagedOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancelled_by", nullable = false, updatable = false)
    private AppUser cancelledBy;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30, updatable = false)
    private OrderStatus previousStatus;

    @Column(name = "order_version_at_cancellation", nullable = false, updatable = false)
    private long orderVersionAtCancellation;

    @Column(
            name = "released_quantity_kg",
            nullable = false,
            precision = 12,
            scale = 3,
            updatable = false
    )
    private BigDecimal releasedQuantityKg;

    protected OrderCancellation() {
    }

    OrderCancellation(
            ManagedOrder order,
            AppUser actor,
            Instant cancelledAt,
            String reason,
            BigDecimal releasedQuantityKg
    ) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.cancelledBy = actor;
        this.cancelledAt = cancelledAt;
        this.reason = reason;
        this.previousStatus = order.getStatus();
        this.orderVersionAtCancellation = order.getVersion();
        this.releasedQuantityKg = releasedQuantityKg;
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public AppUser getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getReason() {
        return reason;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public long getOrderVersionAtCancellation() {
        return orderVersionAtCancellation;
    }

    public BigDecimal getReleasedQuantityKg() {
        return releasedQuantityKg;
    }
}
