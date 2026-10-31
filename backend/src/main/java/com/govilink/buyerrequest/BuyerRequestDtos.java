package com.govilink.buyerrequest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BuyerRequestDtos {

    private BuyerRequestDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 150)
            String buyerName,

            @NotBlank
            @Size(max = 20)
            String phone,

            @NotBlank
            @Size(max = 100)
            String cropName,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 9, fraction = 3)
            BigDecimal quantityKg,

            @NotBlank
            @Size(max = 100)
            String district,

            @NotNull
            @FutureOrPresent
            LocalDate requiredBy,

            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 2)
            BigDecimal maxPricePerKg
    ) {
    }

    public record Response(
            UUID id,
            String buyerName,
            String phone,
            String cropName,
            BigDecimal quantityKg,
            String district,
            LocalDate requiredBy,
            BigDecimal maxPricePerKg,
            BuyerRequestStatus status,
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