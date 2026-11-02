package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.harvest.HarvestListing;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quotations")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_request_id", nullable = false)
    private BuyerRequest buyerRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "buyer_phone", nullable = false, length = 20)
    private String buyerPhone;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "required_by", nullable = false)
    private LocalDate requiredBy;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(
            name = "service_fee",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal serviceFee;

    @Column(
            name = "delivery_fee",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "quotation",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    @OrderBy("lineNumber ASC")
    private List<QuotationLine> lines = new ArrayList<>();

    protected Quotation() {
    }

    public Quotation(
            BuyerRequest buyerRequest,
            AppUser createdBy,
            BigDecimal serviceFee,
            BigDecimal deliveryFee,
            String deliveryAddress
    ) {
        this.buyerRequest = Objects.requireNonNull(
                buyerRequest,
                "Buyer request is required."
        );

        this.createdBy = Objects.requireNonNull(
                createdBy,
                "Staff account is required."
        );

        if (buyerRequest.getId() == null || createdBy.getId() == null) {
            throw new IllegalArgumentException(
                    "Saved buyer request and staff account are required."
            );
        }

        if (buyerRequest.getStatus() != BuyerRequestStatus.OPEN) {
            throw new IllegalStateException(
                    "Only an open buyer request can receive a quotation."
            );
        }

        boolean staffRole = createdBy.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (!createdBy.isActive() || !staffRole) {
            throw new IllegalArgumentException(
                    "An active staff account is required."
            );
        }

        this.buyerName = buyerRequest.getBuyerName();
        this.buyerPhone = buyerRequest.getPhone();
        this.district = buyerRequest.getDistrict();
        this.requiredBy = buyerRequest.getRequiredBy();

        this.serviceFee = fee(serviceFee, "Service fee");
        this.deliveryFee = fee(deliveryFee, "Delivery fee");

        String address = deliveryAddress == null
                ? null
                : deliveryAddress.strip();

        if (address != null && address.length() > 500) {
            throw new IllegalArgumentException(
                    "Delivery address must not exceed 500 characters."
            );
        }

        this.deliveryAddress = address == null || address.isEmpty()
                ? null
                : address;
    }

    public void addLine(
            HarvestListing harvest,
            BigDecimal quantityKg,
            BigDecimal farmerPricePerKg,
            boolean priceConfirmed
    ) {
        if (status != QuotationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only draft quotations can be changed."
            );
        }

        Objects.requireNonNull(harvest, "Harvest listing is required.");

        if (harvest.getId() == null) {
            throw new IllegalArgumentException(
                    "A saved harvest listing is required."
            );
        }

        if (lines.size() >= 50) {
            throw new IllegalArgumentException(
                    "A quotation supports up to 50 harvest sources."
            );
        }

        boolean duplicate = lines.stream().anyMatch(line ->
                line.getHarvestListing().getId().equals(harvest.getId())
        );

        if (duplicate) {
            throw new IllegalArgumentException(
                    "That harvest source is already in this quotation."
            );
        }

        QuotationLine line = new QuotationLine(
                this,
                lines.size() + 1,
                harvest,
                quantityKg,
                farmerPricePerKg,
                priceConfirmed
        );

        BigDecimal nextQuantity = getQuantityKg()
                .add(line.getQuantityKg());

        if (nextQuantity.compareTo(buyerRequest.getQuantityKg()) > 0) {
            throw new IllegalArgumentException(
                    "Quoted quantity exceeds the buyer request."
            );
        }

        lines.add(line);
    }

    private static BigDecimal fee(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " must be supplied and cannot be negative."
            );
        }

        try {
            BigDecimal normalized = value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

            if (normalized.precision() > 12) {
                throw new IllegalArgumentException(
                        field + " exceeds the supported maximum."
                );
            }

            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " supports up to two decimal places.",
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

    public BigDecimal getQuantityKg() {
        return lines.stream()
                .map(QuotationLine::getQuantityKg)
                .reduce(new BigDecimal("0.000"), BigDecimal::add);
    }

    public BigDecimal getProduceSubtotal() {
        return lines.stream()
                .map(QuotationLine::getLineSubtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public BigDecimal getTotal() {
        return getProduceSubtotal()
                .add(serviceFee)
                .add(deliveryFee);
    }

    public UUID getId() { return id; }
    public BuyerRequest getBuyerRequest() { return buyerRequest; }
    public AppUser getCreatedBy() { return createdBy; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerPhone() { return buyerPhone; }
    public String getDistrict() { return district; }
    public LocalDate getRequiredBy() { return requiredBy; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getCurrency() { return currency; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public QuotationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<QuotationLine> getLines() {
        return List.copyOf(lines);
    }
}