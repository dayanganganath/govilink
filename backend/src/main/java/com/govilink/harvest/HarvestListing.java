package com.govilink.harvest;

import com.govilink.farmer.Farmer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "harvest_listings")
public class HarvestListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false)
    private Farmer farmer;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(
            name = "quantity_kg",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityKg;

    @Column(
            name = "reserved_quantity_kg",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal reservedQuantityKg =
            new BigDecimal("0.000");

    @Column(
            name = "expected_price_per_kg",
            precision = 12,
            scale = 2
    )
    private BigDecimal expectedPricePerKg;

    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HarvestStatus status = HarvestStatus.AVAILABLE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HarvestListing() {
    }

    public HarvestListing(
            Farmer farmer,
            String cropName,
            BigDecimal quantityKg,
            BigDecimal expectedPricePerKg,
            LocalDate availableFrom
    ) {
        this.farmer = farmer;
        this.cropName = cropName;
        this.quantityKg = quantityKg;
        this.expectedPricePerKg = expectedPricePerKg;
        this.availableFrom = availableFrom;
    }

    public void reserve(BigDecimal quantity) {
        BigDecimal requested = reservationQuantity(quantity);

        if (status != HarvestStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only an available harvest listing can be reserved."
            );
        }

        if (farmer == null || !farmer.isActive()) {
            throw new IllegalStateException(
                    "Stock cannot be reserved for an inactive farmer."
            );
        }

        if (requested.compareTo(getAvailableQuantityKg()) > 0) {
            throw new IllegalStateException(
                    "Insufficient available stock for this reservation."
            );
        }

        reservedQuantityKg = reservedQuantityKg.add(requested);
    }

    public void releaseReservation(BigDecimal quantity) {
        BigDecimal released = reservationQuantity(quantity);

        if (released.compareTo(reservedQuantityKg) > 0) {
            throw new IllegalStateException(
                    "Released quantity exceeds the reserved stock."
            );
        }

        reservedQuantityKg = reservedQuantityKg.subtract(released);
    }

    public void markSold() {
        finish(HarvestStatus.SOLD);
    }

    public void cancel() {
        finish(HarvestStatus.CANCELLED);
    }

    private void finish(HarvestStatus nextStatus) {
        if (status != HarvestStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only an available harvest listing can be closed."
            );
        }

        if (reservedQuantityKg.signum() > 0) {
            throw new IllegalStateException(
                    "This harvest has active reservations and cannot be closed."
            );
        }

        status = nextStatus;
    }

    private static BigDecimal reservationQuantity(
            BigDecimal quantity
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be greater than zero."
            );
        }

        try {
            BigDecimal normalized = quantity.setScale(
                    3,
                    RoundingMode.UNNECESSARY
            );

            if (normalized.compareTo(
                    new BigDecimal("999999999.999")
            ) > 0) {
                throw new IllegalArgumentException(
                        "Reservation quantity exceeds the supported maximum."
                );
            }

            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Reservation quantity supports up to three decimal places.",
                    exception
            );
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Farmer getFarmer() {
        return farmer;
    }

    public String getCropName() {
        return cropName;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public BigDecimal getReservedQuantityKg() {
        return reservedQuantityKg;
    }

    public BigDecimal getAvailableQuantityKg() {
        if (status != HarvestStatus.AVAILABLE) {
            return new BigDecimal("0.000");
        }

        return quantityKg.subtract(reservedQuantityKg);
    }

    public BigDecimal getExpectedPricePerKg() {
        return expectedPricePerKg;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public HarvestStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}