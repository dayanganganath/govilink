package com.govilink.harvest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HarvestDtos {

    private HarvestDtos() {
    }

    public record CreateRequest(
            @NotNull
            UUID farmerId,

            @NotBlank
            @Size(max = 100)
            String cropName,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 9, fraction = 3)
            BigDecimal quantityKg,

            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 2)
            BigDecimal expectedPricePerKg,

            @NotNull
            LocalDate availableFrom
    ) {
    }

    public record Response(
            UUID id,
            UUID farmerId,
            String farmerName,
            String district,
            String cropName,
            BigDecimal quantityKg,
            BigDecimal expectedPricePerKg,
            LocalDate availableFrom,
            HarvestStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PageResponse(
            List<Response> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}