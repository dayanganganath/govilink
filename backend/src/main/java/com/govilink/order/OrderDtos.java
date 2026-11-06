package com.govilink.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ApproveRequest(
            @NotNull
            @PositiveOrZero
            Long quotationVersion,

            @NotBlank
            @Size(max = 150)
            String buyerContactName,

            @NotNull
            ApprovalChannel channel,

            @NotBlank
            @Size(max = 500)
            String reference,

            @NotNull
            @PastOrPresent
            Instant approvedAt
    ) {
    }

    public record Response(
            UUID id,
            UUID quotationId,
            UUID buyerRequestId,
            OrderStatus status,
            String buyerContactName,
            ApprovalChannel approvalChannel,
            String approvalReference,
            Instant approvedAt,
            Instant recordedAt,
            UUID recordedBy,
            String currency,
            BigDecimal quantityKg,
            BigDecimal produceSubtotal,
            BigDecimal serviceFee,
            BigDecimal deliveryFee,
            BigDecimal total,
            long version,
            List<ReservationResponse> reservations
    ) {
    }

    public record ReservationResponse(
            UUID id,
            UUID quotationLineId,
            UUID harvestListingId,
            String farmerName,
            String cropName,
            BigDecimal quantityKg,
            ReservationStatus status
    ) {
    }
}