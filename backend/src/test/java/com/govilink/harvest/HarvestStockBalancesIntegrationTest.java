package com.govilink.harvest;

import com.govilink.farmer.Farmer;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class HarvestStockBalancesIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HarvestService harvests;

    @Autowired
    private HarvestSearchService search;

    private Farmer farmer;
    private HarvestListing listing;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        farmer = new Farmer(
                "Stock Balance Farmer",
                "SB-" + unique,
                "Matale",
                "Test Village"
        );

        entityManager.persist(farmer);
        entityManager.flush();

        listing = new HarvestListing(
                farmer,
                "Tomato",
                amount("100"),
                amount("200"),
                LocalDate.now(ZoneId.of("Asia/Colombo"))
        );

        entityManager.persist(listing);
        entityManager.flush();
    }

    @Test
    void newListingResponseContainsFullAvailableBalance() {
        var created = harvests.create(new HarvestDtos.CreateRequest(
                farmer.getId(),
                "Carrot",
                amount("25.500"),
                amount("180"),
                LocalDate.now(ZoneId.of("Asia/Colombo"))
        ));

        assertBalances(created, "25.500", "25.500", "0", "0");
    }

    @Test
    void detailResponseSeparatesAvailableReservedAndCollectedStock() {
        listing.reserve(amount("60"));
        listing.collectReservation(amount("20"));
        entityManager.flush();

        UUID id = listing.getId();
        entityManager.clear();

        var response = harvests.getById(id);

        assertBalances(response, "100", "40", "40", "20");
    }

    @Test
    void listResponseContainsCurrentStockBalances() {
        listing.reserve(amount("100"));
        listing.collectReservation(amount("40"));
        entityManager.flush();

        var page = harvests.list(farmer.getId(), 0, 10);

        assertEquals(1, page.content().size());
        assertBalances(page.content().get(0), "100", "0", "60", "40");
    }

    @Test
    void searchResponseContainsCurrentStockBalances() {
        listing.reserve(amount("60"));
        listing.collectReservation(amount("20"));
        entityManager.flush();

        var page = search.search(
                "Tomato",
                "Matale",
                HarvestStatus.AVAILABLE,
                farmer.getId(),
                0,
                10
        );

        assertEquals(1, page.content().size());
        assertBalances(page.content().get(0), "100", "40", "40", "20");
    }

    @Test
    void releasedReservationRestoresAvailableBalance() {
        listing.reserve(amount("60"));
        entityManager.flush();

        assertBalances(
                harvests.getById(listing.getId()),
                "100", "40", "60", "0"
        );

        listing.releaseReservation(amount("60"));
        entityManager.flush();

        assertBalances(
                harvests.getById(listing.getId()),
                "100", "100", "0", "0"
        );
    }

    @Test
    void fullyCollectedStockHasNoAvailableBalance() {
        listing.reserve(amount("100"));
        listing.collectReservation(amount("100"));
        entityManager.flush();

        var response = harvests.getById(listing.getId());

        assertEquals(HarvestStatus.AVAILABLE, response.status());
        assertBalances(response, "100", "0", "0", "100");
    }

    @Test
    void closedListingHasZeroAvailableStockAndRetainsCollectionHistory() {
        listing.reserve(amount("20"));
        listing.collectReservation(amount("20"));
        listing.markSold();
        entityManager.flush();

        var response = harvests.getById(listing.getId());

        assertEquals(HarvestStatus.SOLD, response.status());
        assertBalances(response, "100", "0", "0", "20");
    }

    private void assertBalances(
            HarvestDtos.Response response,
            String listed,
            String available,
            String reserved,
            String collected
    ) {
        assertAmount(listed, response.quantityKg());
        assertAmount(available, response.availableQuantityKg());
        assertAmount(reserved, response.reservedQuantityKg());
        assertAmount(collected, response.collectedQuantityKg());
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, amount(expected).compareTo(actual));
    }
}