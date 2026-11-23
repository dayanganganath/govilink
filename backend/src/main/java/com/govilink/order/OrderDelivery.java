package com.govilink.order;

import com.govilink.identity.AppUser;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_deliveries")
public class OrderDelivery {

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
            name = "recorded_by",
            nullable = false,
            updatable = false
    )
    private AppUser recordedBy;

    @Column(
            name = "receiver_name",
            nullable = false,
            length = 150,
            updatable = false
    )
    private String receiverName;

    @Column(
            name = "delivered_at",
            nullable = false,
            updatable = false
    )
    private Instant deliveredAt;

    @Column(
            name = "recorded_at",
            nullable = false,
            updatable = false
    )
    private Instant recordedAt;

    @Column(nullable = false, length = 500, updatable = false)
    private String reference;

    @Column(
            name = "order_version_at_delivery",
            nullable = false,
            updatable = false
    )
    private long orderVersionAtDelivery;

    protected OrderDelivery() {
    }

    OrderDelivery(
            ManagedOrder order,
            AppUser recordedBy,
            String receiverName,
            Instant deliveredAt,
            Instant recordedAt,
            String reference
    ) {
        this.order = order;
        this.recordedBy = recordedBy;
        this.receiverName = receiverName;
        this.deliveredAt = deliveredAt;
        this.recordedAt = recordedAt;
        this.reference = reference;
        this.orderVersionAtDelivery = order.getVersion();
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getReference() {
        return reference;
    }

    public long getOrderVersionAtDelivery() {
        return orderVersionAtDelivery;
    }
}