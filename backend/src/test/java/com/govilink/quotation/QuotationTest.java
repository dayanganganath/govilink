package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.farmer.Farmer;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestStatus;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class QuotationTest {

    private BuyerRequest buyer;
    private AppUser staff;
    private Farmer farmer;
    private HarvestListing harvest;

    @BeforeEach
    void setUp() {
        buyer = saved(new BuyerRequest(
                "Test Buyer",
                "TEST-BUYER",
                "Tomato",
                amount("500"),
                "Matale",
                LocalDate.now().plusDays(7),
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
                "TEST-FARMER",
                "Matale",
                "Test Village"
        ));

        harvest = source(
                "Tomato",
                "1000",
                LocalDate.now()
        );
    }

    @Test
    void snapshotsBuyerDetailsAndCreatesDraft() {
        Quotation quote = draft();

        ReflectionTestUtils.setField(
                buyer,
                "buyerName",
                "Changed Buyer"
        );

        assertEquals("Test Buyer", quote.getBuyerName());
        assertEquals("TEST-BUYER", quote.getBuyerPhone());
        assertEquals(QuotationStatus.DRAFT, quote.getStatus());
        assertEquals("LKR", quote.getCurrency());
        assertEquals("Test Address", quote.getDeliveryAddress());
    }

    @Test
    void combinesSourcesAndAddsFeesOnce() {
        Quotation quote = draft();

        quote.addLine(
                harvest,
                amount("200"),
                amount("180"),
                true
        );

        quote.addLine(
                source("Tomato", "1000", LocalDate.now()),
                amount("300"),
                amount("170"),
                false
        );

        assertEquals(amount("500.000"), quote.getQuantityKg());
        assertEquals(amount("87000.00"), quote.getProduceSubtotal());
        assertEquals(amount("94000.00"), quote.getTotal());
        assertEquals(2, quote.getLines().get(1).getLineNumber());
        assertFalse(quote.getLines().get(1).isPriceConfirmed());
    }

    @Test
    void draftDoesNotReserveStockAndKeepsQuotedPrice() {
        Quotation quote = draft();

        quote.addLine(
                harvest,
                amount("100"),
                amount("180"),
                true
        );

        ReflectionTestUtils.setField(
                harvest,
                "expectedPricePerKg",
                amount("200")
        );

        assertEquals(
                amount("180.00"),
                quote.getLines().get(0).getFarmerPricePerKg()
        );

        assertEquals(amount("1000"), harvest.getQuantityKg());
        assertEquals(HarvestStatus.AVAILABLE, harvest.getStatus());
        assertEquals(0L, harvest.getVersion());
    }

    @Test
    void rejectsDuplicateHarvestWithoutChangingTheDraft() {
        Quotation quote = draft();

        quote.addLine(harvest, amount("100"), amount("180"), true);

        assertThrows(
                IllegalArgumentException.class,
                () -> quote.addLine(
                        harvest, amount("100"), amount("180"), true
                )
        );

        assertEquals(1, quote.getLines().size());
        assertEquals(amount("100.000"), quote.getQuantityKg());
    }

    @Test
    void rejectsQuantityAboveBuyerDemand() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        harvest, amount("501"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsQuantityAboveTheHarvestListing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        source("Tomato", "10", LocalDate.now()),
                        amount("11"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsDifferentCrop() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        source("Carrot", "1000", LocalDate.now()),
                        amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsDifferentDistrict() {
        ReflectionTestUtils.setField(farmer, "district", "Kandy");

        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        harvest, amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsHarvestAvailableAfterTheRequiredDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        source(
                                "Tomato",
                                "1000",
                                buyer.getRequiredBy().plusDays(1)
                        ),
                        amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsClosedHarvest() {
        harvest.markSold();

        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        harvest, amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsInactiveFarmer() {
        ReflectionTestUtils.setField(farmer, "active", false);

        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        harvest, amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void rejectsPriceAboveBuyerBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> draft().addLine(
                        harvest, amount("10"), amount("220.01"), true
                )
        );
    }

    @Test
    void requiresActiveStaffForCreation() {
        staff.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                this::draft
        );
    }

    @Test
    void rejectsNegativeFees() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Quotation(
                        buyer, staff, amount("-1"), amount("0"), null
                )
        );
    }

    @Test
    void exposesAnUnmodifiableLineList() {
        Quotation quote = draft();

        quote.addLine(harvest, amount("10"), amount("180"), true);

        assertThrows(
                UnsupportedOperationException.class,
                () -> quote.getLines().clear()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = QuotationStatus.class,
            names = "DRAFT",
            mode = EnumSource.Mode.EXCLUDE
    )
    void nonDraftQuotationsCannotBeChanged(
            QuotationStatus status
    ) {
        Quotation quote = draft();

        ReflectionTestUtils.setField(quote, "status", status);

        assertThrows(
                IllegalStateException.class,
                () -> quote.addLine(
                        harvest, amount("10"), amount("180"), true
                )
        );
    }

    @Test
    void limitsTheNumberOfHarvestSources() {
        Quotation quote = draft();

        for (int i = 0; i < 50; i++) {
            quote.addLine(
                    source("Tomato", "1000", LocalDate.now()),
                    amount("1"),
                    amount("180"),
                    true
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> quote.addLine(
                        harvest, amount("1"), amount("180"), true
                )
        );

        assertEquals(50, quote.getLines().size());
    }

    private Quotation draft() {
        return new Quotation(
                buyer,
                staff,
                amount("4500"),
                amount("2500"),
                " Test Address "
        );
    }

    private HarvestListing source(
            String crop,
            String quantity,
            LocalDate available
    ) {
        return saved(new HarvestListing(
                farmer,
                crop,
                amount(quantity),
                amount("180"),
                available
        ));
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private <T> T saved(T value) {
        ReflectionTestUtils.setField(
                value,
                "id",
                UUID.randomUUID()
        );

        return value;
    }
}