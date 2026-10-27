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

    @Column(name = "created_at", nullable = false, updatable = false)
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