package com.govilink.harvest;

import com.govilink.farmer.Farmer;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class HarvestReservationTest {

    private Farmer farmer;
    private HarvestListing harvest;

    @BeforeEach
    void setUp() {
        farmer = new Farmer(
                "Test Farmer",
                "TEST-RESERVATION",
                "Matale",
                "Test Village"
        );

        harvest = new HarvestListing(
                farmer,
                "Tomato",
                amount("100.000"),
                amount("200.00"),
                LocalDate.of(2030, 1, 1)
        );
    }

    @Test
    void startsWithNoReservations() {
        assertAmount("0.000", harvest.getReservedQuantityKg());
        assertAmount("100.000", harvest.getAvailableQuantityKg());
    }

    @Test
    void reservesStockWithoutChangingTotalQuantity() {
        harvest.reserve(amount("30"));

        assertAmount("100.000", harvest.getQuantityKg());
        assertAmount("30.000", harvest.getReservedQuantityKg());
        assertAmount("70.000", harvest.getAvailableQuantityKg());

        assertEquals(
                HarvestStatus.AVAILABLE,
                harvest.getStatus()
        );
    }

    @Test
    void supportsFractionalReservationsWithoutRounding() {
        harvest.reserve(amount("0.001"));
        harvest.reserve(amount("0.002"));

        assertAmount("0.003", harvest.getReservedQuantityKg());
        assertAmount("99.997", harvest.getAvailableQuantityKg());
    }

    @Test
    void allowsReservationOfAllRemainingStock() {
        harvest.reserve(amount("30"));
        harvest.reserve(amount("70"));

        assertAmount("100.000", harvest.getReservedQuantityKg());
        assertAmount("0.000", harvest.getAvailableQuantityKg());
    }

    @Test
    void rejectsReservationAboveRemainingStockWithoutChangingIt() {
        harvest.reserve(amount("80"));

        assertThrows(
                IllegalStateException.class,
                () -> harvest.reserve(amount("20.001"))
        );

        assertAmount("80.000", harvest.getReservedQuantityKg());
        assertAmount("20.000", harvest.getAvailableQuantityKg());
        assertAmount("100.000", harvest.getQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD", "CANCELLED"})
    void rejectsReservationsForClosedHarvests(String status) {
        closeHarvest(status);

        assertThrows(
                IllegalStateException.class,
                () -> harvest.reserve(amount("1"))
        );

        assertAmount("0.000", harvest.getReservedQuantityKg());
        assertAmount("0.000", harvest.getAvailableQuantityKg());
    }

    @Test
    void rejectsReservationsForInactiveFarmers() {
        ReflectionTestUtils.setField(
                farmer,
                "active",
                false
        );

        assertThrows(
                IllegalStateException.class,
                () -> harvest.reserve(amount("1"))
        );

        assertAmount("0.000", harvest.getReservedQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "0.0001",
            "1.2345",
            "1000000000"
    })
    void rejectsInvalidReservationQuantities(String quantity) {
        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.reserve(amount(quantity))
        );

        assertAmount("0.000", harvest.getReservedQuantityKg());
        assertAmount("100.000", harvest.getAvailableQuantityKg());
    }

    @Test
    void rejectsNullReservationQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.reserve(null)
        );

        assertAmount("0.000", harvest.getReservedQuantityKg());
    }

    @Test
    void releasesPartOfTheReservedStock() {
        harvest.reserve(amount("60"));
        harvest.releaseReservation(amount("20"));

        assertAmount("40.000", harvest.getReservedQuantityKg());
        assertAmount("60.000", harvest.getAvailableQuantityKg());
        assertAmount("100.000", harvest.getQuantityKg());
    }

    @Test
    void releasesAllReservedStock() {
        harvest.reserve(amount("100"));
        harvest.releaseReservation(amount("100"));

        assertAmount("0.000", harvest.getReservedQuantityKg());
        assertAmount("100.000", harvest.getAvailableQuantityKg());
    }

    @Test
    void rejectsReleaseAboveReservedStockWithoutChangingIt() {
        harvest.reserve(amount("30"));

        assertThrows(
                IllegalStateException.class,
                () -> harvest.releaseReservation(amount("30.001"))
        );

        assertAmount("30.000", harvest.getReservedQuantityKg());
        assertAmount("70.000", harvest.getAvailableQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "0.0001",
            "1.2345",
            "1000000000"
    })
    void rejectsInvalidReleaseQuantities(String quantity) {
        harvest.reserve(amount("30"));

        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.releaseReservation(amount(quantity))
        );

        assertAmount("30.000", harvest.getReservedQuantityKg());
    }

    @Test
    void rejectsNullReleaseQuantity() {
        harvest.reserve(amount("30"));

        assertThrows(
                IllegalArgumentException.class,
                () -> harvest.releaseReservation(null)
        );

        assertAmount("30.000", harvest.getReservedQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD", "CANCELLED"})
    void preventsClosingHarvestsWithActiveReservations(
            String status
    ) {
        harvest.reserve(amount("30"));

        assertThrows(
                IllegalStateException.class,
                () -> closeHarvest(status)
        );

        assertEquals(
                HarvestStatus.AVAILABLE,
                harvest.getStatus()
        );

        assertAmount("30.000", harvest.getReservedQuantityKg());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD", "CANCELLED"})
    void allowsClosingAfterAllReservationsAreReleased(
            String status
    ) {
        harvest.reserve(amount("30"));
        harvest.releaseReservation(amount("30"));

        closeHarvest(status);

        assertEquals(
                HarvestStatus.valueOf(status),
                harvest.getStatus()
        );

        assertAmount("0.000", harvest.getReservedQuantityKg());
        assertAmount("0.000", harvest.getAvailableQuantityKg());
    }

    private void closeHarvest(String status) {
        if ("SOLD".equals(status)) {
            harvest.markSold();
        } else {
            harvest.cancel();
        }
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {
        assertNotNull(actual);

        assertEquals(
                0,
                amount(expected).compareTo(actual),
                "Expected " + expected + " but received " + actual
        );
    }
}