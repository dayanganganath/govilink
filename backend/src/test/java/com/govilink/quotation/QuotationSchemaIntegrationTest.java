package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestRepository;
import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class QuotationSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FarmerRepository farmers;

    @Autowired
    private HarvestRepository harvests;

    @Autowired
    private BuyerRequestRepository buyers;

    @Autowired
    private AppUserRepository users;

    private UUID buyerId;
    private UUID userId;
    private UUID harvestId;
    private Farmer farmer;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        LocalDate date = LocalDate.now().plusDays(7);

        farmer = farmers.saveAndFlush(new Farmer(
                "Quote Test Farmer",
                "QT-" + unique,
                "Matale",
                "Test Village"
        ));

        harvestId = harvests.saveAndFlush(new HarvestListing(
                farmer,
                "Tomato",
                new BigDecimal("1000.000"),
                new BigDecimal("180.00"),
                date
        )).getId();

        buyerId = buyers.saveAndFlush(new BuyerRequest(
                "Quote Test Buyer",
                "QB-" + unique,
                "Tomato",
                new BigDecimal("500.000"),
                "Matale",
                date,
                new BigDecimal("220.00")
        )).getId();

        userId = users.saveAndFlush(new AppUser(
                "quote-" + unique + "@example.com",
                "Test Coordinator",
                "test-only-not-a-login-password",
                Set.of(UserRole.COORDINATOR)
        )).getId();
    }

    @Test
    void storesDraftAndCalculatesLineSubtotal() {
        UUID quoteId = insertQuote("DRAFT", "4500.00");

        insertLine(
                quoteId,
                harvestId,
                1,
                "500.000",
                "180.00"
        );

        String status = jdbc.queryForObject(
                "SELECT status FROM quotations WHERE id = ?",
                String.class,
                quoteId
        );

        BigDecimal total = jdbc.queryForObject(
                """
                SELECT q.service_fee
                       + q.delivery_fee
                       + sum(l.line_subtotal)
                FROM quotations q
                JOIN quotation_lines l
                  ON l.quotation_id = q.id
                WHERE q.id = ?
                GROUP BY q.id
                """,
                BigDecimal.class,
                quoteId
        );

        assertEquals("DRAFT", status);

        assertEquals(
                0,
                new BigDecimal("97000.00").compareTo(total)
        );
    }

    @Test
    void allowsMultipleHarvestSourcesInOneDraft() {
        UUID secondHarvest = harvests.saveAndFlush(
                new HarvestListing(
                        farmer,
                        "Tomato",
                        new BigDecimal("100.000"),
                        new BigDecimal("170.00"),
                        LocalDate.now().plusDays(7)
                )
        ).getId();

        UUID quoteId = insertQuote("DRAFT", "0.00");

        insertLine(
                quoteId,
                harvestId,
                1,
                "250.000",
                "180.00"
        );

        insertLine(
                quoteId,
                secondHarvest,
                2,
                "100.000",
                "170.00"
        );

        assertEquals(
                2,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM quotation_lines
                        WHERE quotation_id = ?
                        """,
                        Integer.class,
                        quoteId
                )
        );
    }

    @Test
    void databaseRoundingMatchesThePricingCalculator() {
        UUID quoteId = insertQuote("DRAFT", "0.00");

        insertLine(
                quoteId,
                harvestId,
                1,
                "0.001",
                "5.00"
        );

        BigDecimal subtotal = jdbc.queryForObject(
                """
                SELECT line_subtotal
                FROM quotation_lines
                WHERE quotation_id = ?
                """,
                BigDecimal.class,
                quoteId
        );

        var expected = QuotationPricing.calculate(
                new BigDecimal("0.001"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertEquals(
                0,
                expected.produceSubtotal().compareTo(subtotal)
        );
    }

    @Test
    void rejectsDuplicateHarvestSourcesInTheSameQuotation() {
        UUID quoteId = insertQuote("DRAFT", "0.00");

        insertLine(
                quoteId,
                harvestId,
                1,
                "10.000",
                "180.00"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertLine(
                        quoteId,
                        harvestId,
                        2,
                        "10.000",
                        "180.00"
                )
        );
    }

    @Test
    void rejectsNonPositiveQuantities() {
        UUID quoteId = insertQuote("DRAFT", "0.00");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertLine(
                        quoteId,
                        harvestId,
                        1,
                        "0.000",
                        "180.00"
                )
        );
    }

    @Test
    void rejectsMissingHarvestReferences() {
        UUID quoteId = insertQuote("DRAFT", "0.00");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertLine(
                        quoteId,
                        UUID.randomUUID(),
                        1,
                        "10.000",
                        "180.00"
                )
        );
    }

    @Test
    void rejectsNegativeServiceFees() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertQuote("DRAFT", "-0.01")
        );
    }

    @Test
    void issuedQuotationRequiresAnExpiryTime() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertQuote("ISSUED", "0.00")
        );
    }

    private UUID insertQuote(
            String status,
            String serviceFee
    ) {
        UUID id = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO quotations (
                    id,
                    buyer_request_id,
                    created_by,
                    buyer_name,
                    buyer_phone,
                    district,
                    required_by,
                    service_fee,
                    delivery_fee,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    now(), now()
                )
                """,
                id,
                buyerId,
                userId,
                "Quote Test Buyer",
                "QT-BUYER",
                "Matale",
                LocalDate.now().plusDays(7),
                new BigDecimal(serviceFee),
                new BigDecimal("2500.00"),
                status
        );

        return id;
    }

    private void insertLine(
            UUID quoteId,
            UUID sourceId,
            int number,
            String quantity,
            String price
    ) {
        jdbc.update(
                """
                INSERT INTO quotation_lines (
                    id,
                    quotation_id,
                    line_number,
                    harvest_listing_id,
                    crop_name,
                    quantity_kg,
                    farmer_price_per_kg
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                quoteId,
                number,
                sourceId,
                "Tomato",
                new BigDecimal(quantity),
                new BigDecimal(price)
        );
    }
}