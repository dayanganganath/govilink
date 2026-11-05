package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.farmer.Farmer;
import com.govilink.harvest.HarvestListing;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class QuotationIssuancePolicyTest {

    private static final Instant NOW =
            Instant.parse("2030-01-01T06:00:00Z");

    private static final Instant EXPIRY =
            Instant.parse("2030-01-02T06:00:00Z");

    private BuyerRequest buyer;
    private AppUser staff;
    private Farmer farmer;
    private HarvestListing harvest;
    private Quotation quote;
    private UUID lineId;

    @BeforeEach
    void setUp() {
        buyer = saved(new BuyerRequest(
                "Test Buyer",
                "TEST-B",
                "Tomato",
                amount("500"),
                "Matale",
                LocalDate.of(2030, 1, 8),
                amount("220")
        ));

        staff = saved(new AppUser(
                "staff@example.com",
                "Test Staff",
                "test-only-password-hash",
                Set.of(UserRole.COORDINATOR)
        ));

        farmer = saved(new Farmer(
                "Test Farmer",
                "TEST-F",
                "Matale",
                "Test Village"
        ));

        harvest = saved(new HarvestListing(
                farmer,
                "Tomato",
                amount("1000"),
                amount("180"),
                LocalDate.of(2030, 1, 1)
        ));

        quote = saved(new Quotation(
                buyer,
                staff,
                amount("100"),
                amount("200"),
                null
        ));

        quote.addLine(
                harvest,
                amount("500"),
                amount("180"),
                false
        );

        lineId = UUID.randomUUID();

        ReflectionTestUtils.setField(
                quote.getLines().get(0),
                "id",
                lineId
        );
    }

    @Test
    void validatesAnExplicitlyReviewedDraftWithoutChangingIt() {
        assertDoesNotThrow(this::validate);

        assertEquals(
                QuotationStatus.DRAFT,
                quote.getStatus()
        );

        assertFalse(
                quote.getLines().get(0).isPriceConfirmed()
        );

        assertEquals(
                amount("1000"),
                harvest.getQuantityKg()
        );
    }

    @Test
    void rejectsExpiryThatIsNotInTheFuture() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote, staff, NOW, NOW, Set.of(lineId)
                )
        );
    }

    @Test
    void usesSriLankanCalendarDateForExpiry() {
        Instant tooLate =
                Instant.parse("2030-01-08T20:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote, staff, NOW, tooLate, Set.of(lineId)
                )
        );
    }

    @Test
    void rejectsPartialAllocation() {
        ReflectionTestUtils.setField(
                buyer, "quantityKg", amount("600")
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void requiresConfirmationOfAllCurrentLines() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote, staff, NOW, EXPIRY, Set.of()
                )
        );
    }

    @Test
    void rejectsConfirmationOfUnrelatedLines() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote,
                        staff,
                        NOW,
                        EXPIRY,
                        Set.of(lineId, UUID.randomUUID())
                )
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = QuotationStatus.class,
            names = "DRAFT",
            mode = EnumSource.Mode.EXCLUDE
    )
    void rejectsNonDraftStatuses(QuotationStatus status) {
        ReflectionTestUtils.setField(
                quote, "status", status
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsInactiveIssuer() {
        staff.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                this::validate
        );
    }

    @Test
    void rejectsCustomerIssuer() {
        AppUser customer = saved(new AppUser(
                "buyer@example.com",
                "Test Buyer",
                "test-only-password-hash",
                Set.of(UserRole.BUYER)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote, customer, NOW, EXPIRY, Set.of(lineId)
                )
        );
    }

    @Test
    void rejectsClosedBuyerRequest() {
        ReflectionTestUtils.setField(
                buyer,
                "status",
                BuyerRequestStatus.CANCELLED
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsChangedBuyerDetails() {
        ReflectionTestUtils.setField(
                buyer, "buyerName", "Changed Buyer"
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsInactiveFarmer() {
        ReflectionTestUtils.setField(
                farmer, "active", false
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsSoldHarvest() {
        harvest.markSold();

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsReducedStock() {
        ReflectionTestUtils.setField(
                harvest, "quantityKg", amount("499")
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsLaterHarvestAvailability() {
        ReflectionTestUtils.setField(
                harvest,
                "availableFrom",
                LocalDate.of(2030, 1, 9)
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsChangedHarvestCrop() {
        ReflectionTestUtils.setField(
                harvest, "cropName", "Carrot"
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsChangedFarmerDistrict() {
        ReflectionTestUtils.setField(
                farmer, "district", "Kandy"
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsPriceAboveTheCurrentBudget() {
        ReflectionTestUtils.setField(
                buyer, "maxPricePerKg", amount("179")
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsUnsavedDraft() {
        ReflectionTestUtils.setField(
                quote, "id", null
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsUnsavedLine() {
        ReflectionTestUtils.setField(
                quote.getLines().get(0),
                "id",
                null
        );

        assertThrows(
                IllegalStateException.class,
                this::validate
        );
    }

    @Test
    void rejectsRequestWhoseRequiredDateHasPassed() {
        Instant later =
                Instant.parse("2030-01-09T06:00:00Z");

        assertThrows(
                IllegalStateException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote,
                        staff,
                        later,
                        later.plusSeconds(3600),
                        Set.of(lineId)
                )
        );
    }

    @Test
    void allowsExpiryBeforeTheEndOfTheRequiredSriLankanDay() {
        Instant lastSecond =
                Instant.parse("2030-01-08T18:29:59Z");

        assertDoesNotThrow(
                () -> QuotationIssuancePolicy.validate(
                        quote,
                        staff,
                        NOW,
                        lastSecond,
                        Set.of(lineId)
                )
        );
    }

    @Test
    void rejectsAnEmptySavedDraft() {
        Quotation empty = saved(new Quotation(
                buyer,
                staff,
                amount("0"),
                amount("0"),
                null
        ));

        assertThrows(
                IllegalStateException.class,
                () -> QuotationIssuancePolicy.validate(
                        empty, staff, NOW, EXPIRY, Set.of()
                )
        );
    }

    @Test
    void requiresExplicitReviewEvenForAPreviouslyConfirmedPrice() {
        ReflectionTestUtils.setField(
                quote.getLines().get(0),
                "priceConfirmed",
                true
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> QuotationIssuancePolicy.validate(
                        quote, staff, NOW, EXPIRY, Set.of()
                )
        );
    }

    private void validate() {
        QuotationIssuancePolicy.validate(
                quote,
                staff,
                NOW,
                EXPIRY,
                Set.of(lineId)
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private <T> T saved(T value) {
        ReflectionTestUtils.setField(
                value, "id", UUID.randomUUID()
        );

        return value;
    }
}