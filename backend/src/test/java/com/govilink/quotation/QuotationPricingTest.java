package com.govilink.quotation;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class QuotationPricingTest {

    @Test
    void calculatesProduceServiceDeliveryAndTotalSeparately() {
        var result = calculate(
                "500",
                "180",
                "4500",
                "2500"
        );

        assertEquals(
                new BigDecimal("90000.00"),
                result.produceSubtotal()
        );

        assertEquals(
                new BigDecimal("4500.00"),
                result.serviceFee()
        );

        assertEquals(
                new BigDecimal("2500.00"),
                result.deliveryFee()
        );

        assertEquals(
                new BigDecimal("97000.00"),
                result.total()
        );

        assertEquals("LKR", result.currency());
    }

    @Test
    void roundsTheProduceSubtotalToTwoDecimalPlaces() {
        var result = calculate(
                "0.001",
                "5",
                "0",
                "0"
        );

        assertEquals(
                new BigDecimal("0.01"),
                result.produceSubtotal()
        );

        assertEquals(
                new BigDecimal("0.01"),
                result.total()
        );
    }

    @Test
    void allowsExplicitZeroFees() {
        var result = calculate(
                "12.345",
                "180.50",
                "0",
                "0"
        );

        assertEquals(
                new BigDecimal("2228.27"),
                result.total()
        );

        assertEquals(
                new BigDecimal("0.00"),
                result.serviceFee()
        );

        assertEquals(
                new BigDecimal("0.00"),
                result.deliveryFee()
        );
    }

    @Test
    void normalizesQuantityAndMoneyWithoutLosingValue() {
        var result = calculate(
                "2.5000",
                "180.500",
                "10.000",
                "20.000"
        );

        assertEquals(
                new BigDecimal("2.500"),
                result.quantityKg()
        );

        assertEquals(
                new BigDecimal("180.50"),
                result.farmerPricePerKg()
        );

        assertEquals(
                new BigDecimal("481.25"),
                result.total()
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void requiresAllFourAmountsToBeExplicit(int missingField) {
        BigDecimal[] values = {
                new BigDecimal("500"),
                new BigDecimal("180"),
                new BigDecimal("4500"),
                new BigDecimal("2500")
        };

        values[missingField] = null;

        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationPricing.calculate(
                        values[0],
                        values[1],
                        values[2],
                        values[3]
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-0.001",
            "1.0001",
            "1000000000"
    })
    void rejectsInvalidQuantities(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate(value, "180", "0", "0")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-0.01",
            "1.001",
            "10000000000"
    })
    void rejectsInvalidFarmerPrices(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate("500", value, "0", "0")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-0.01",
            "1.001",
            "10000000000"
    })
    void rejectsInvalidServiceFees(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate("500", "180", value, "0")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-0.01",
            "1.001",
            "10000000000"
    })
    void rejectsInvalidDeliveryFees(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculate("500", "180", "0", value)
        );
    }

    private QuotationPricing.Breakdown calculate(
            String quantity,
            String price,
            String service,
            String delivery
    ) {
        return QuotationPricing.calculate(
                new BigDecimal(quantity),
                new BigDecimal(price),
                new BigDecimal(service),
                new BigDecimal(delivery)
        );
    }
}