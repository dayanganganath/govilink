package com.govilink.order;

import com.govilink.identity.AppUser;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_dispatches")
public class OrderDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private ManagedOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "dispatched_by",
            nullable = false,
            updatable = false
    )
    private AppUser dispatchedBy;

    @Column(
            name = "dispatched_at",
            nullable = false,
            updatable = false
    )
    private Instant dispatchedAt;

    @Column(nullable = false, length = 500, updatable = false)
    private String reference;

    @Column(
            name = "order_version_at_dispatch",
            nullable = false,
            updatable = false
    )
    private long orderVersionAtDispatch;

    protected OrderDispatch() {
    }

    OrderDispatch(
            ManagedOrder order,
            AppUser dispatchedBy,
            Instant dispatchedAt,
            String reference
    ) {
        this.order = order;
        this.dispatchedBy = dispatchedBy;
        this.dispatchedAt = dispatchedAt;
        this.reference = reference;
        this.orderVersionAtDispatch = order.getVersion();
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public AppUser getDispatchedBy() {
        return dispatchedBy;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public String getReference() {
        return reference;
    }

    public long getOrderVersionAtDispatch() {
        return orderVersionAtDispatch;
    }
}