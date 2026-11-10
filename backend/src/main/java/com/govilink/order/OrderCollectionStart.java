package com.govilink.order;

import com.govilink.identity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_collection_starts")
public class OrderCollectionStart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private ManagedOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by", nullable = false)
    private AppUser startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false, length = 500)
    private String reference;

    @Column(name = "order_version_at_start", nullable = false)
    private long orderVersionAtStart;

    protected OrderCollectionStart() {
    }

    OrderCollectionStart(
            ManagedOrder order,
            AppUser startedBy,
            Instant startedAt,
            String reference
    ) {
        this.order = order;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.reference = reference;
        this.orderVersionAtStart = order.getVersion();
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public AppUser getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public String getReference() {
        return reference;
    }

    public long getOrderVersionAtStart() {
        return orderVersionAtStart;
    }
}