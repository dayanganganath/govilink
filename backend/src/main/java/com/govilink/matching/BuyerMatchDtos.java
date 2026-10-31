package com.govilink.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BuyerMatchDtos {

    private BuyerMatchDtos() {
    }

    public record Match(
            UUID id,
            UUID farmerId,
            String farmerName,
            String phone,
            String district,
            String cropName,
            BigDecimal quantityKg,
            BigDecimal expectedPricePerKg,
            LocalDate availableFrom,
            boolean priceConfirmationNeeded
    ) {
    }

    public record PageResponse(
            List<Match> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }
}