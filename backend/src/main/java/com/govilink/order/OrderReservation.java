package com.govilink.order;

import com.govilink.harvest.HarvestListing;
import com.govilink.quotation.QuotationLine;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_reservations")
public class OrderReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ManagedOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_line_id", nullable = false)
    private QuotationLine quotationLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "harvest_listing_id", nullable = false)
    private HarvestListing harvestListing;

    @Column(
            name = "quantity_kg",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderReservation() {
    }

    OrderReservation(
            ManagedOrder order,
            QuotationLine line,
            Instant now
    ) {
        this.order = Objects.requireNonNull(
                order,
                "Order is required."
        );

        this.quotationLine = Objects.requireNonNull(
                line,
                "Quotation line is required."
        );

        this.reservedAt = Objects.requireNonNull(
                now,
                "Reservation time is required."
        );

        if (line.getId() == null
                || line.getHarvestListing() == null
                || line.getHarvestListing().getId() == null) {
            throw new IllegalArgumentException(
                    "A saved quotation line and harvest listing are required."
            );
        }

        if (!order.getQuotation().getId().equals(
                line.getQuotation().getId()
        )) {
            throw new IllegalArgumentException(
                    "The quotation line does not belong to this order's quotation."
            );
        }

        if (line.getQuantityKg() == null
                || line.getQuantityKg().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be greater than zero."
            );
        }

        this.harvestListing = line.getHarvestListing();
        this.quantityKg = line.getQuantityKg();
    }

    public UUID getId() {
        return id;
    }

    public ManagedOrder getOrder() {
        return order;
    }

    public QuotationLine getQuotationLine() {
        return quotationLine;
    }

    public HarvestListing getHarvestListing() {
        return harvestListing;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getVersion() {
        return version;
    }
}