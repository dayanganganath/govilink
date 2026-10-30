package com.govilink.harvest;

import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class HarvestSearchIntegrationTest {

    @Autowired
    private HarvestSearchService service;

    @Autowired
    private HarvestRepository harvestRepository;

    @Autowired
    private FarmerRepository farmerRepository;

    private final String token = "H"
            + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12);

    @Test
    void combinesCropDistrictAndStatusFiltersIgnoringCaseAndSpaces() {
        Farmer mataleFarmer = createFarmer("Matale");
        Farmer kandyFarmer = createFarmer("Kandy");

        HarvestListing expected = createListing(
                mataleFarmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        createListing(
                mataleFarmer,
                token + " Tomato",
                HarvestStatus.SOLD
        );

        createListing(
                mataleFarmer,
                token + " Carrot",
                HarvestStatus.AVAILABLE
        );

        createListing(
                kandyFarmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        var result = service.search(
                "  " + token.toUpperCase() + " ToMaTo  ",
                "  mAtAlE  ",
                HarvestStatus.AVAILABLE,
                null,
                0,
                10
        );

        assertAll(
                () -> assertEquals(1L, result.totalElements()),
                () -> assertEquals(1, result.content().size()),
                () -> assertEquals(
                        expected.getId(),
                        result.content().get(0).id()
                ),
                () -> assertEquals(
                        HarvestStatus.AVAILABLE,
                        result.content().get(0).status()
                ),
                () -> assertEquals(
                        "Matale",
                        result.content().get(0).district()
                )
        );
    }

    @Test
    void farmerFilterExcludesOtherFarmersListings() {
        Farmer firstFarmer = createFarmer("Matale");
        Farmer secondFarmer = createFarmer("Matale");

        HarvestListing expected = createListing(
                firstFarmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        createListing(
                secondFarmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        var result = service.search(
                token,
                null,
                null,
                firstFarmer.getId(),
                0,
                10
        );

        assertEquals(1L, result.totalElements());
        assertEquals(expected.getId(), result.content().get(0).id());

        assertEquals(
                firstFarmer.getId(),
                result.content().get(0).farmerId()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void searchTreatsLikeSpecialCharactersAsLiteralText(String symbol) {
        Farmer farmer = createFarmer("Matale");

        HarvestListing expected = createListing(
                farmer,
                token + symbol + " Tomato",
                HarvestStatus.AVAILABLE
        );

        createListing(
                farmer,
                token + "X Tomato",
                HarvestStatus.AVAILABLE
        );

        var result = service.search(
                token + symbol,
                null,
                null,
                farmer.getId(),
                0,
                10
        );

        assertEquals(1L, result.totalElements());
        assertEquals(expected.getId(), result.content().get(0).id());
    }

    @Test
    void paginationReturnsAllMatchingListingsWithoutDuplicates() {
        Farmer farmer = createFarmer("Matale");

        HarvestListing first = createListing(
                farmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        HarvestListing second = createListing(
                farmer,
                token + " Carrot",
                HarvestStatus.AVAILABLE
        );

        HarvestListing third = createListing(
                farmer,
                token + " Pumpkin",
                HarvestStatus.AVAILABLE
        );

        var firstPage = service.search(
                token,
                null,
                null,
                farmer.getId(),
                0,
                2
        );

        var secondPage = service.search(
                token,
                null,
                null,
                farmer.getId(),
                1,
                2
        );

        assertAll(
                () -> assertEquals(3L, firstPage.totalElements()),
                () -> assertEquals(2, firstPage.totalPages()),
                () -> assertEquals(0, firstPage.page()),
                () -> assertEquals(2, firstPage.size()),
                () -> assertEquals(2, firstPage.content().size()),
                () -> assertTrue(firstPage.hasNext()),
                () -> assertFalse(firstPage.hasPrevious()),
                () -> assertEquals(1, secondPage.page()),
                () -> assertEquals(1, secondPage.content().size()),
                () -> assertFalse(secondPage.hasNext()),
                () -> assertTrue(secondPage.hasPrevious())
        );

        Set<UUID> firstIds = firstPage.content()
                .stream()
                .map(HarvestDtos.Response::id)
                .collect(Collectors.toSet());

        Set<UUID> secondIds = secondPage.content()
                .stream()
                .map(HarvestDtos.Response::id)
                .collect(Collectors.toSet());

        assertTrue(firstIds.stream().noneMatch(secondIds::contains));

        Set<UUID> combinedIds = new HashSet<>(firstIds);
        combinedIds.addAll(secondIds);

        assertEquals(
                Set.of(first.getId(), second.getId(), third.getId()),
                combinedIds
        );
    }

    @Test
    void blankTextFiltersAreIgnoredAndMissingStatusIncludesAllStatuses() {
        Farmer farmer = createFarmer("Matale");

        createListing(
                farmer,
                token + " Tomato",
                HarvestStatus.AVAILABLE
        );

        createListing(
                farmer,
                token + " Carrot",
                HarvestStatus.CANCELLED
        );

        var result = service.search(
                "   ",
                "   ",
                null,
                farmer.getId(),
                0,
                10
        );

        assertEquals(2L, result.totalElements());

        Set<HarvestStatus> statuses = result.content()
                .stream()
                .map(HarvestDtos.Response::status)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(HarvestStatus.AVAILABLE, HarvestStatus.CANCELLED),
                statuses
        );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",
            "0, 0",
            "0, 101"
    })
    void invalidPaginationReturnsBadRequest(int page, int size) {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.search(
                        null,
                        null,
                        null,
                        null,
                        page,
                        size
                )
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"query", "district"})
    void oversizedTextFilterReturnsBadRequest(String field) {
        String tooLong = "x".repeat(101);

        String query = field.equals("query") ? tooLong : null;
        String district = field.equals("district") ? tooLong : null;

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> service.search(
                        query,
                        district,
                        null,
                        null,
                        0,
                        10
                )
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private Farmer createFarmer(String district) {
        String phone = "TEST-"
                + UUID.randomUUID().toString().substring(0, 8);

        return farmerRepository.saveAndFlush(new Farmer(
                "Harvest Search Test Farmer",
                phone,
                district,
                "Test Village"
        ));
    }

    private HarvestListing createListing(
            Farmer farmer,
            String cropName,
            HarvestStatus status
    ) {
        HarvestListing listing = new HarvestListing(
                farmer,
                cropName,
                new BigDecimal("10.000"),
                new BigDecimal("180.00"),
                LocalDate.of(2026, 10, 27)
        );

        if (status == HarvestStatus.SOLD) {
            listing.markSold();
        } else if (status == HarvestStatus.CANCELLED) {
            listing.cancel();
        }

        return harvestRepository.saveAndFlush(listing);
    }
}