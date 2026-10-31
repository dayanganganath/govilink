package com.govilink.matching;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestRepository;
import com.govilink.harvest.HarvestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BuyerHarvestMatchingIntegrationTest {

    @Autowired
    private BuyerMatchService service;

    @Autowired
    private BuyerRequestRepository buyerRepository;

    @Autowired
    private FarmerRepository farmerRepository;

    @Autowired
    private HarvestRepository harvestRepository;

    private final String token =
            UUID.randomUUID().toString().substring(0, 8);

    private final String crop = "Tomato-" + token;
    private final String district = "Matale-" + token;

    private final LocalDate today =
            LocalDate.now(ZoneId.of("Asia/Colombo"));

    @Test
    void includesExactBoundariesAndIgnoresCropAndDistrictCase() {
        BuyerRequest buyer = createBuyer(
                new BigDecimal("220.00"),
                today.plusDays(7)
        );

        HarvestListing expected = createHarvest(
                crop.toUpperCase(Locale.ROOT),
                district.toUpperCase(Locale.ROOT),
                new BigDecimal("500.000"),
                new BigDecimal("220.00"),
                today.plusDays(7),
                true,
                HarvestStatus.AVAILABLE
        );

        var result = service.matches(buyer.getId(), 0, 10);

        assertAll(
                () -> assertEquals(1L, result.totalElements()),
                () -> assertEquals(
                        expected.getId(),
                        result.content().get(0).id()
                ),
                () -> assertFalse(
                        result.content().get(0).priceConfirmationNeeded()
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "crop",
            "district",
            "quantity",
            "price",
            "date",
            "sold",
            "cancelled",
            "inactive"
    })
    void excludesHarvestsThatFailAnyMatchingRule(String failedRule) {
        BuyerRequest buyer = createBuyer(
                new BigDecimal("220.00"),
                today.plusDays(7)
        );

        HarvestListing expected = createEligibleHarvest();

        HarvestStatus status = switch (failedRule) {
            case "sold" -> HarvestStatus.SOLD;
            case "cancelled" -> HarvestStatus.CANCELLED;
            default -> HarvestStatus.AVAILABLE;
        };

        createHarvest(
                failedRule.equals("crop")
                        ? "Carrot-" + token
                        : crop,

                failedRule.equals("district")
                        ? "Kandy-" + token
                        : district,

                new BigDecimal(
                        failedRule.equals("quantity")
                                ? "499.999"
                                : "500.000"
                ),

                new BigDecimal(
                        failedRule.equals("price")
                                ? "220.01"
                                : "220.00"
                ),

                today.plusDays(
                        failedRule.equals("date") ? 8 : 7
                ),

                !failedRule.equals("inactive"),
                status
        );

        var result = service.matches(buyer.getId(), 0, 10);

        assertEquals(1L, result.totalElements());
        assertEquals(expected.getId(), result.content().get(0).id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"buyer", "harvest"})
    void supportsOptionalPricesAndFlagsUnpricedHarvests(
            String missingPrice
    ) {
        BuyerRequest buyer = createBuyer(
                missingPrice.equals("buyer")
                        ? null
                        : new BigDecimal("220.00"),
                today.plusDays(7)
        );

        createHarvest(
                crop,
                district,
                new BigDecimal("500.000"),
                missingPrice.equals("harvest")
                        ? null
                        : new BigDecimal("999.00"),
                today,
                true,
                HarvestStatus.AVAILABLE
        );

        var result = service.matches(buyer.getId(), 0, 10);

        assertEquals(1L, result.totalElements());

        assertEquals(
                missingPrice.equals("harvest"),
                result.content().get(0).priceConfirmationNeeded()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = BuyerRequestStatus.class,
            names = {"FULFILLED", "CANCELLED"}
    )
    void rejectsClosedBuyerRequests(BuyerRequestStatus status) {
        BuyerRequest buyer = createBuyer(
                null,
                today.plusDays(7)
        );

        ReflectionTestUtils.setField(buyer, "status", status);
        buyerRepository.saveAndFlush(buyer);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.matches(buyer.getId(), 0, 10)
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void rejectsExpiredBuyerRequest() {
        BuyerRequest buyer = createBuyer(
                null,
                today.minusDays(1)
        );

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.matches(buyer.getId(), 0, 10)
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void unknownBuyerRequestReturns404() {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.matches(UUID.randomUUID(), 0, 10)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void paginationReturnsMatchesWithoutDuplicates() {
        BuyerRequest buyer = createBuyer(
                null,
                today.plusDays(7)
        );

        HarvestListing first = createEligibleHarvest();
        HarvestListing second = createEligibleHarvest();
        HarvestListing third = createEligibleHarvest();

        var firstPage = service.matches(buyer.getId(), 0, 2);
        var secondPage = service.matches(buyer.getId(), 1, 2);

        assertAll(
                () -> assertEquals(3L, firstPage.totalElements()),
                () -> assertEquals(2, firstPage.totalPages()),
                () -> assertEquals(2, firstPage.content().size()),
                () -> assertTrue(firstPage.hasNext()),
                () -> assertFalse(firstPage.hasPrevious()),
                () -> assertEquals(1, secondPage.content().size()),
                () -> assertFalse(secondPage.hasNext()),
                () -> assertTrue(secondPage.hasPrevious())
        );

        Set<UUID> ids = new HashSet<>();

        firstPage.content().forEach(
                match -> assertTrue(ids.add(match.id()))
        );

        secondPage.content().forEach(
                match -> assertTrue(ids.add(match.id()))
        );

        assertEquals(
                Set.of(first.getId(), second.getId(), third.getId()),
                ids
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",
            "0, 0",
            "0, 101"
    })
    void rejectsInvalidPagination(int page, int size) {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.matches(
                        UUID.randomUUID(),
                        page,
                        size
                )
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private BuyerRequest createBuyer(
            BigDecimal maximumPrice,
            LocalDate requiredBy
    ) {
        return buyerRepository.saveAndFlush(
                new BuyerRequest(
                        "Matching Test Buyer",
                        "TEST-BUYER",
                        crop,
                        new BigDecimal("500.000"),
                        district,
                        requiredBy,
                        maximumPrice
                )
        );
    }

    private HarvestListing createEligibleHarvest() {
        return createHarvest(
                crop,
                district,
                new BigDecimal("500.000"),
                new BigDecimal("220.00"),
                today.plusDays(7),
                true,
                HarvestStatus.AVAILABLE
        );
    }

    private HarvestListing createHarvest(
            String cropName,
            String location,
            BigDecimal quantity,
            BigDecimal price,
            LocalDate availableFrom,
            boolean active,
            HarvestStatus status
    ) {
        Farmer farmer = new Farmer(
                "Matching Test Farmer",
                "TEST-" + UUID.randomUUID().toString().substring(0, 8),
                location,
                "Test Village"
        );

        if (!active) {
            ReflectionTestUtils.setField(farmer, "active", false);
        }

        farmer = farmerRepository.saveAndFlush(farmer);

        HarvestListing listing = new HarvestListing(
                farmer,
                cropName,
                quantity,
                price,
                availableFrom
        );

        if (status == HarvestStatus.SOLD) {
            listing.markSold();
        } else if (status == HarvestStatus.CANCELLED) {
            listing.cancel();
        }

        return harvestRepository.saveAndFlush(listing);
    }
}