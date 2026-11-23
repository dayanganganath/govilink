package com.govilink.harvest;

import com.govilink.farmer.Farmer;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HarvestCollectionStockTest {

    private HarvestListing harvest;

    @BeforeEach
    void setUp() {
        Farmer farmer = new Farmer(
                "Collection Stock Farmer",
                "TEST-COLLECTION",
                "Matale",
                "Test Village"
        );

        harvest = new HarvestListing(
                farmer,
                "Tomato",
                amount("100"),
                amount("200"),
                LocalDate.of(2030, 1, 1)
        );
    }

    @Test
    void startsWithNoCollectedStock() {
        assertAmount("0", harvest.getCollectedQuantityKg());
        assertAmount("100", harvest.getAvailableQuantityKg());
    }

    @Test
    void collectionTransfersReservedStockWithoutChangingAvailability() {
        harvest.reserve(amount("60"));

        harvest.collectReservation(amount("25"));

        assertAmount("100", harvest.getQuantityKg());
        assertAmount("35", harvest.getReservedQuantityKg());
        assertAmount("25", harvest.getCollectedQuantityKg());
        assertAmount("40", harvest.getAvailableQuantityKg());
    }

    @Test
    void releasingTheRemainingReservationDoesNotReleaseCollectedStock() {
        harvest.reserve(amount("60"));
        harvest.collectReservation(amount("25"));

        harvest.releaseReservation(amount("35"));

        assertAmount("0", harvest.getReservedQuantityKg());
        assertAmount("25", harvest.getCollectedQuantityKg());
        assertAmount("75", harvest.getAvailableQuantityKg());
    }

    @Test
    void collectedStockCannotBeReservedAgain() {
        harvest.reserve(amount("100"));
        harvest.collectReservation(amount("100"));

        assertThrows(
                IllegalStateException.class,
                () -> harvest.reserve(amount("0.001"))
        );

        assertAmount("100", harvest.getQuantityKg());
        assertAmount("0", harvest.getReservedQuantityKg());
        assertAmount("100", harvest.getCollectedQuantityKg());
        assertAmount("0", harvest.getAvailableQuantityKg());
    }

    @Test
    void rejectsCollectionAboveReservedBalanceWithoutChangingStock() {
        harvest.reserve(amount("30"));

        assertThrows(
                IllegalStateException.class,
                () -> harvest.collectReservation(amount("30.001"))
        );

        assertAmount("30", harvest.getReservedQuantityKg());
        assertAmount("0", harvest.getCollectedQuantityKg());
        assertAmount("70", harvest.getAvailableQuantityKg());
    }

    @Test
    void cannotCollectUnreservedStock() {
        assertThrows(
                IllegalStateException.class,
                () -> harvest.collectReservation(amount("1"))
        );

        assertAmount("0", harvest.getCollectedQuantityKg());
        assertAmount("100", harvest.getAvailableQuantityKg());
    }

    @Test
    void handlesFractionalCollectionsExactly() {
        harvest.reserve(amount("1"));

        harvest.collectReservation(amount("0.001"));
        harvest.collectReservation(amount("0.002"));

        assertAmount("0.997", harvest.getReservedQuantityKg());
        assertAmount("0.003", harvest.getCollectedQuantityKg());
        assertAmount("99", harvest.getAvailableQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "0.0001",
            "1.2345",
            "1000000000"
    })
    void rejectsInvalidCollectionQuantities(String quantity) {
        harvest.reserve(amount("100"));

        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.collectReservation(amount(quantity))
        );

        assertAmount("100", harvest.getReservedQuantityKg());
        assertAmount("0", harvest.getCollectedQuantityKg());
    }

    @Test
    void rejectsNullCollectionQuantity() {
        harvest.reserve(amount("100"));

        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.collectReservation(null)
        );

        assertAmount("100", harvest.getReservedQuantityKg());
        assertAmount("0", harvest.getCollectedQuantityKg());
    }

    @Test
    void allowsFurtherReservationsOnlyFromUncollectedAvailableStock() {
        harvest.reserve(amount("60"));
        harvest.collectReservation(amount("60"));

        harvest.reserve(amount("40"));
        harvest.collectReservation(amount("40"));

        assertAmount("100", harvest.getQuantityKg());
        assertAmount("100", harvest.getCollectedQuantityKg());
        assertAmount("0", harvest.getReservedQuantityKg());
        assertAmount("0", harvest.getAvailableQuantityKg());
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(
                0,
                amount(expected).compareTo(actual),
                "Expected " + expected + " but received " + actual
        );
    }
}