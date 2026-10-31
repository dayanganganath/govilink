package com.govilink.buyerrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "buyer_requests")
public class BuyerRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(
            name = "quantity_kg",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal quantityKg;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "required_by", nullable = false)
    private LocalDate requiredBy;

    @Column(
            name = "max_price_per_kg",
            precision = 12,
            scale = 2
    )
    private BigDecimal maxPricePerKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuyerRequestStatus status = BuyerRequestStatus.OPEN;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuyerRequest() {
    }

    public BuyerRequest(
            String buyerName,
            String phone,
            String cropName,
            BigDecimal quantityKg,
            String district,
            LocalDate requiredBy,
            BigDecimal maxPricePerKg
    ) {
        this.buyerName = buyerName;
        this.phone = phone;
        this.cropName = cropName;
        this.quantityKg = quantityKg;
        this.district = district;
        this.requiredBy = requiredBy;
        this.maxPricePerKg = maxPricePerKg;
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

    public String getBuyerName() {
        return buyerName;
    }

    public String getPhone() {
        return phone;
    }

    public String getCropName() {
        return cropName;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public String getDistrict() {
        return district;
    }

    public LocalDate getRequiredBy() {
        return requiredBy;
    }

    public BigDecimal getMaxPricePerKg() {
        return maxPricePerKg;
    }

    public BuyerRequestStatus getStatus() {
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