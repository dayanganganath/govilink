package com.govilink.harvest;

import com.govilink.common.ApiExceptionHandler;
import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HarvestStatusControllerTest {

    private static final UUID LISTING_ID =
            UUID.fromString("56e21060-f3f5-4c77-aa7e-fdbd82ba791d");

    private static final String ENDPOINT =
            "/api/v1/harvest-listings/" + LISTING_ID + "/status";

    private HarvestRepository repository;
    private FarmerRepository farmerRepository;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        repository = mock(HarvestRepository.class);
        farmerRepository = mock(FarmerRepository.class);

        HarvestService service = new HarvestService(
                repository,
                farmerRepository
        );

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HarvestController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest
    @EnumSource(
            value = HarvestStatus.class,
            names = {"SOLD", "CANCELLED"}
    )
    void availableListingCanBeClosed(HarvestStatus target)
            throws Exception {
        HarvestListing listing = availableListing();

        when(repository.findById(LISTING_ID))
                .thenReturn(Optional.of(listing));

        when(repository.saveAndFlush(listing))
                .thenReturn(listing);

        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(target.name(), "0"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(target.name()))
                .andExpect(jsonPath("$.cropName").value("Tomato"))
                .andExpect(jsonPath("$.quantityKg").value(10));

        assertEquals(target, listing.getStatus());

        verify(repository).findById(LISTING_ID);
        verify(repository).saveAndFlush(listing);
        verifyNoInteractions(farmerRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"SOLD\"}",
            "{\"status\":\"SOLD\",\"version\":null}",
            "{\"status\":\"SOLD\",\"version\":-1}"
    })
    void invalidVersionReturns400BeforeRepositoryCalls(String requestBody)
            throws Exception {
        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.version").isNotEmpty());

        verifyNoInteractions(repository, farmerRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"version\":0}",
            "{\"status\":null,\"version\":0}"
    })
    void missingStatusReturns400BeforeRepositoryCalls(String requestBody)
            throws Exception {
        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.status").isNotEmpty());

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void unknownStatusReturns400BeforeRepositoryCalls()
            throws Exception {
        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("UNKNOWN", "0"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid request body"));

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void availableIsNotAnAcceptedTargetStatus() throws Exception {
        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("AVAILABLE", "0"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Status must be SOLD or CANCELLED."));

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void missingListingReturns404WithoutSaving() throws Exception {
        when(repository.findById(LISTING_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("SOLD", "0"))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Harvest listing not found."));

        verify(repository).findById(LISTING_ID);
        verify(repository, never()).saveAndFlush(any(HarvestListing.class));
        verifyNoInteractions(farmerRepository);
    }

    @Test
    void staleVersionReturns409WithoutChangingOrSavingListing()
            throws Exception {
        HarvestListing listing = availableListing();

        when(repository.findById(LISTING_ID))
                .thenReturn(Optional.of(listing));

        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("SOLD", "1"))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Harvest listing was changed by another request. "
                                + "Reload the latest details and try again."
                ));

        assertEquals(HarvestStatus.AVAILABLE, listing.getStatus());

        verify(repository, never()).saveAndFlush(any(HarvestListing.class));
        verifyNoInteractions(farmerRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = HarvestStatus.class,
            names = {"SOLD", "CANCELLED"}
    )
    void closedListingCannotBeChangedAgain(HarvestStatus originalStatus)
            throws Exception {
        HarvestListing listing = availableListing();

        if (originalStatus == HarvestStatus.SOLD) {
            listing.markSold();
        } else {
            listing.cancel();
        }

        HarvestStatus target = originalStatus == HarvestStatus.SOLD
                ? HarvestStatus.CANCELLED
                : HarvestStatus.SOLD;

        when(repository.findById(LISTING_ID))
                .thenReturn(Optional.of(listing));

        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(target.name(), "0"))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Only an available harvest listing can be closed."
                ));

        assertEquals(originalStatus, listing.getStatus());

        verify(repository, never()).saveAndFlush(any(HarvestListing.class));
        verifyNoInteractions(farmerRepository);
    }

    @Test
    void databaseOptimisticLockFailureReturnsHarvestConflict()
            throws Exception {
        HarvestListing listing = availableListing();

        when(repository.findById(LISTING_ID))
                .thenReturn(Optional.of(listing));

        when(repository.saveAndFlush(listing))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        HarvestListing.class,
                        LISTING_ID
                ));

        mockMvc.perform(
                        patch(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("SOLD", "0"))
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "Harvest listing was changed by another request. "
                                + "Reload the latest details and try again."
                ));

        verify(repository).saveAndFlush(listing);
        verifyNoInteractions(farmerRepository);
    }

    private HarvestListing availableListing() {
        Farmer farmer = new Farmer(
                "Demo Farmer",
                "TEST-001",
                "Matale",
                "Demo Village"
        );

        return new HarvestListing(
                farmer,
                "Tomato",
                new BigDecimal("10.000"),
                new BigDecimal("180.00"),
                LocalDate.of(2026, 10, 27)
        );
    }

    private String body(String targetStatus, String version) {
        return """
                {
                  "status": "%s",
                  "version": %s
                }
                """.formatted(targetStatus, version);
    }
}