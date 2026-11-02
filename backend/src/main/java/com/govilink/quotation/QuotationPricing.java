package com.govilink.quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class QuotationPricing {

    private static final BigDecimal MAX_QUANTITY =
            new BigDecimal("999999999.999");

    private static final BigDecimal MAX_PRICE_OR_FEE =
            new BigDecimal("9999999999.99");

    private QuotationPricing() {
    }

    public static Breakdown calculate(
            BigDecimal quantityKg,
            BigDecimal farmerPricePerKg,
            BigDecimal serviceFee,
            BigDecimal deliveryFee
    ) {
        BigDecimal quantity = validate(
                quantityKg,
                "Quantity",
                3,
                MAX_QUANTITY,
                false
        );

        BigDecimal price = validate(
                farmerPricePerKg,
                "Farmer price",
                2,
                MAX_PRICE_OR_FEE,
                false
        );

        BigDecimal service = validate(
                serviceFee,
                "Service fee",
                2,
                MAX_PRICE_OR_FEE,
                true
        );

        BigDecimal delivery = validate(
                deliveryFee,
                "Delivery fee",
                2,
                MAX_PRICE_OR_FEE,
                true
        );

        BigDecimal produceSubtotal = quantity.multiply(price)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = produceSubtotal
                .add(service)
                .add(delivery);

        return new Breakdown(
                quantity,
                price,
                produceSubtotal,
                service,
                delivery,
                total,
                "LKR"
        );
    }

    private static BigDecimal validate(
            BigDecimal value,
            String field,
            int scale,
            BigDecimal maximum,
            boolean zeroAllowed
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        if (value.signum() < 0
                || (!zeroAllowed && value.signum() == 0)) {
            throw new IllegalArgumentException(
                    field + (zeroAllowed
                            ? " must be zero or greater."
                            : " must be greater than zero.")
            );
        }

        if (value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    field + " exceeds the supported maximum."
            );
        }

        try {
            return value.setScale(
                    scale,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " supports up to "
                            + scale + " decimal places.",
                    exception
            );
        }
    }

    public record Breakdown(
            BigDecimal quantityKg,
            BigDecimal farmerPricePerKg,
            BigDecimal produceSubtotal,
            BigDecimal serviceFee,
            BigDecimal deliveryFee,
            BigDecimal total,
            String currency
    ) {
    }
}