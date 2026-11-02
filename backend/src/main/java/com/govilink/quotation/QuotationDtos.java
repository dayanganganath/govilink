package com.govilink.quotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QuotationDtos {

    private QuotationDtos() {
    }

    public record CreateDraft(
            @NotNull
            UUID buyerRequestId,

            @NotNull
            @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2)
            BigDecimal serviceFee,

            @NotNull
            @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2)
            BigDecimal deliveryFee,

            @Size(max = 500)
            String deliveryAddress,

            @NotNull
            @Size(min = 1, max = 50)
            List<@NotNull @Valid CreateLine> lines
    ) {
    }

    public record CreateLine(
            @NotNull
            UUID harvestListingId,

            @NotNull
            @DecimalMin("0.001")
            @Digits(integer = 9, fraction = 3)
            BigDecimal quantityKg,

            @NotNull
            @DecimalMin("0.01")
            @Digits(integer = 10, fraction = 2)
            BigDecimal farmerPricePerKg,

            @NotNull
            Boolean priceConfirmed
    ) {
    }

    public record Response(
            UUID id,
            UUID buyerRequestId,
            UUID createdBy,
            String buyerName,
            String buyerPhone,
            String district,
            LocalDate requiredBy,
            String deliveryAddress,
            QuotationStatus status,
            String currency,
            BigDecimal quantityKg,
            BigDecimal produceSubtotal,
            BigDecimal serviceFee,
            BigDecimal deliveryFee,
            BigDecimal total,
            boolean priceConfirmationNeeded,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<LineResponse> lines
    ) {
    }

    public record LineResponse(
            UUID id,
            int lineNumber,
            UUID harvestListingId,
            UUID farmerId,
            String farmerName,
            String cropName,
            BigDecimal quantityKg,
            BigDecimal farmerPricePerKg,
            BigDecimal lineSubtotal,
            boolean priceConfirmed
    ) {
    }
}