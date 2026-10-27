package com.govilink.harvest;

import com.govilink.common.ApiExceptionHandler;
import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HarvestControllerTest {

    private static final String BASE = "/api/v1/harvest-listings";

    private static final UUID FARMER_ID =
            UUID.fromString("87bc0103-f7fb-432f-a219-292e928164c7");

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
    @NullSource
    @ValueSource(strings = "180.00")
    void createAcceptsOptionalPriceAndNormalizesValues(String price)
            throws Exception {
        Farmer farmer = activeFarmer();

        when(farmerRepository.findById(FARMER_ID))
                .thenReturn(Optional.of(farmer));

        when(repository.saveAndFlush(any(HarvestListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("125.5", price))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmerId")
                        .value(FARMER_ID.toString()))
                .andExpect(jsonPath("$.farmerName")
                        .value("Demo Farmer"))
                .andExpect(jsonPath("$.cropName").value("Tomato"))
                .andExpect(jsonPath("$.quantityKg").value(125.5))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableFrom")
                        .value("2026-10-27"));

        ArgumentCaptor<HarvestListing> captor =
                ArgumentCaptor.forClass(HarvestListing.class);

        verify(repository).saveAndFlush(captor.capture());

        HarvestListing saved = captor.getValue();

        assertAll(
                () -> assertSame(farmer, saved.getFarmer()),
                () -> assertEquals("Tomato", saved.getCropName()),
                () -> assertEquals(
                        new BigDecimal("125.500"),
                        saved.getQuantityKg()
                ),
                () -> assertEquals(
                        HarvestStatus.AVAILABLE,
                        saved.getStatus()
                )
        );

        if (price == null) {
            assertNull(saved.getExpectedPricePerKg());
        } else {
            assertEquals(
                    new BigDecimal("180.00"),
                    saved.getExpectedPricePerKg()
            );
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "0",
            "-1",
            "0.0001",
            "1000000000",
            "125.1234"
    })
    void invalidQuantityReturns400WithoutRepositoryCalls(String quantity)
            throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(quantity, "180"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.errors.quantityKg").isNotEmpty());

        verifyNoInteractions(repository, farmerRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "180.001"})
    void invalidPriceReturns400WithoutRepositoryCalls(String price)
            throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("125.5", price))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expectedPricePerKg")
                        .isNotEmpty());

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void blankCropReturns400WithoutRepositoryCalls() throws Exception {
        String body = createBody("125.5", "180")
                .replace("  Tomato  ", "   ");

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cropName").isNotEmpty());

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void missingFarmerReturns404WithoutSaving() throws Exception {
        when(farmerRepository.findById(FARMER_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("125.5", "180"))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Farmer not found."));

        verify(farmerRepository).findById(FARMER_ID);
        verifyNoInteractions(repository);
    }

    @Test
    void inactiveFarmerReturns409WithoutSaving() throws Exception {
        Farmer inactiveFarmer = mock(Farmer.class);

        when(inactiveFarmer.isActive()).thenReturn(false);

        when(farmerRepository.findById(FARMER_ID))
                .thenReturn(Optional.of(inactiveFarmer));

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("125.5", "180"))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Cannot create a harvest listing for an inactive farmer."
                ));

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",
            "0, 0",
            "0, 101"
    })
    void invalidPaginationReturns400WithoutRepositoryCalls(
            int page,
            int size
    ) throws Exception {
        mockMvc.perform(
                        get(BASE)
                                .param("page", String.valueOf(page))
                                .param("size", String.valueOf(size))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));

        verifyNoInteractions(repository, farmerRepository);
    }

    @Test
    void farmerFilterReturnsMappedPageWithStableSorting()
            throws Exception {
        Farmer farmer = activeFarmer();

        HarvestListing listing = new HarvestListing(
                farmer,
                "Tomato",
                new BigDecimal("125.500"),
                new BigDecimal("180.00"),
                LocalDate.of(2026, 10, 27)
        );

        PageRequest expectedPage = PageRequest.of(
                0,
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        when(repository.findByFarmer_Id(FARMER_ID, expectedPage))
                .thenReturn(new PageImpl<>(
                        List.of(listing),
                        expectedPage,
                        1
                ));

        mockMvc.perform(
                        get(BASE)
                                .param("farmerId", FARMER_ID.toString())
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].farmerId")
                        .value(FARMER_ID.toString()))
                .andExpect(jsonPath("$.content[0].cropName")
                        .value("Tomato"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(repository).findByFarmer_Id(FARMER_ID, expectedPage);
        verifyNoInteractions(farmerRepository);
    }

    @Test
    void missingListingReturns404() throws Exception {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Harvest listing not found."));

        verify(repository).findById(id);
    }

    @Test
    void invalidListingIdReturns400WithoutRepositoryCalls()
            throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid parameter"));

        verifyNoInteractions(repository, farmerRepository);
    }

    private Farmer activeFarmer() {
        Farmer farmer = mock(Farmer.class);

        when(farmer.getId()).thenReturn(FARMER_ID);
        when(farmer.getFullName()).thenReturn("Demo Farmer");
        when(farmer.getDistrict()).thenReturn("Matale");
        when(farmer.isActive()).thenReturn(true);

        return farmer;
    }

    private String createBody(String quantity, String price) {
        return """
                {
                  "farmerId": "%s",
                  "cropName": "  Tomato  ",
                  "quantityKg": %s,
                  "expectedPricePerKg": %s,
                  "availableFrom": "2026-10-27"
                }
                """.formatted(FARMER_ID, quantity, price);
    }
}