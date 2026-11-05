package com.govilink.quotation;

import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "quotation_lines")
public class QuotationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "harvest_listing_id", nullable = false)
    private HarvestListing harvestListing;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "farmer_price_per_kg", nullable = false, precision = 12, scale = 2)
    private BigDecimal farmerPricePerKg;

    @Column(name = "price_confirmed", nullable = false)
    private boolean priceConfirmed;

    protected QuotationLine() {
    }

    QuotationLine(
            Quotation quotation,
            int lineNumber,
            HarvestListing harvest,
            BigDecimal quantityKg,
            BigDecimal farmerPricePerKg,
            boolean priceConfirmed
    ) {
        if (harvest.getStatus() != HarvestStatus.AVAILABLE
                || !harvest.getFarmer().isActive()) {
            throw new IllegalArgumentException(
                    "An available harvest from an active farmer is required."
            );
        }

        String requestedCrop = quotation.getBuyerRequest()
                .getCropName().strip();

        if (!harvest.getCropName().strip()
                .equalsIgnoreCase(requestedCrop)) {
            throw new IllegalArgumentException(
                    "Harvest crop does not match the buyer request."
            );
        }

        if (!harvest.getFarmer().getDistrict().strip()
                .equalsIgnoreCase(quotation.getDistrict().strip())) {
            throw new IllegalArgumentException(
                    "Harvest district does not match the quotation."
            );
        }

        if (harvest.getAvailableFrom()
                .isAfter(quotation.getRequiredBy())) {
            throw new IllegalArgumentException(
                    "Harvest is available after the required date."
            );
        }

        var pricing = QuotationPricing.calculate(
                quantityKg,
                farmerPricePerKg,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal budget = quotation.getBuyerRequest()
                .getMaxPricePerKg();

        if (budget != null
                && pricing.farmerPricePerKg().compareTo(budget) > 0) {
            throw new IllegalArgumentException(
                    "Farmer price exceeds the buyer budget."
            );
        }

        if (pricing.quantityKg()
                .compareTo(harvest.getQuantityKg()) > 0) {
            throw new IllegalArgumentException(
                    "Quoted quantity exceeds this harvest listing."
            );
        }

        this.quotation = quotation;
        this.lineNumber = lineNumber;
        this.harvestListing = harvest;
        this.cropName = harvest.getCropName();
        this.quantityKg = pricing.quantityKg();
        this.farmerPricePerKg = pricing.farmerPricePerKg();
        this.priceConfirmed = priceConfirmed;
    }

    void confirmPrice() {
        priceConfirmed = true;
    }

    public BigDecimal getLineSubtotal() {
        return quantityKg.multiply(farmerPricePerKg)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public int getLineNumber() { return lineNumber; }
    public HarvestListing getHarvestListing() { return harvestListing; }
    public String getCropName() { return cropName; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public BigDecimal getFarmerPricePerKg() { return farmerPricePerKg; }
    public boolean isPriceConfirmed() { return priceConfirmed; }
}