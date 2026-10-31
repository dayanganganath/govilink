package com.govilink.buyerrequest;

import com.govilink.common.ApiExceptionHandler;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BuyerRequestControllerTest {

    private static final String BASE = "/api/v1/buyer-requests";

    private BuyerRequestRepository repository;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        repository = mock(BuyerRequestRepository.class);

        BuyerRequestService service = new BuyerRequestService(repository);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new BuyerRequestController(service))
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
    @ValueSource(strings = "220.00")
    void createNormalizesFieldsAndAcceptsOptionalPrice(String price)
            throws Exception {
        when(repository.saveAndFlush(any(BuyerRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "500",
                                        price,
                                        futureDateJson(),
                                        null
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buyerName").value("Demo Buyer"))
                .andExpect(jsonPath("$.phone").value("TEST-BUYER"))
                .andExpect(jsonPath("$.cropName").value("Tomato"))
                .andExpect(jsonPath("$.district").value("Matale"))
                .andExpect(jsonPath("$.quantityKg").value(500))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<BuyerRequest> captor =
                ArgumentCaptor.forClass(BuyerRequest.class);

        verify(repository).saveAndFlush(captor.capture());

        BuyerRequest saved = captor.getValue();

        assertAll(
                () -> assertEquals("Demo Buyer", saved.getBuyerName()),
                () -> assertEquals("TEST-BUYER", saved.getPhone()),
                () -> assertEquals("Tomato", saved.getCropName()),
                () -> assertEquals("Matale", saved.getDistrict()),
                () -> assertEquals(
                        new BigDecimal("500.000"),
                        saved.getQuantityKg()
                ),
                () -> assertEquals(
                        BuyerRequestStatus.OPEN,
                        saved.getStatus()
                )
        );

        if (price == null) {
            assertNull(saved.getMaxPricePerKg());
        } else {
            assertEquals(
                    new BigDecimal("220.00"),
                    saved.getMaxPricePerKg()
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
            "500.1234"
    })
    void invalidQuantityReturns400WithoutSaving(String quantity)
            throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        quantity,
                                        "220",
                                        futureDateJson(),
                                        null
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantityKg").isNotEmpty());

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "220.001"})
    void invalidPriceReturns400WithoutSaving(String price)
            throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "500",
                                        price,
                                        futureDateJson(),
                                        null
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxPricePerKg").isNotEmpty());

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "buyerName",
            "phone",
            "cropName",
            "district"
    })
    void blankRequiredTextReturns400WithoutSaving(String field)
            throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        "500",
                                        "220",
                                        futureDateJson(),
                                        field
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors." + field).isNotEmpty());

        verifyNoInteractions(repository);
    }

    @Test
    void pastRequiredDateReturns400WithoutSaving() throws Exception {
        String pastDate = "\""
                + LocalDate.now().minusDays(1)
                + "\"";

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("500", "220", pastDate, null))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requiredBy").isNotEmpty());

        verifyNoInteractions(repository);
    }

    @Test
    void nullRequiredDateReturns400WithoutSaving() throws Exception {
        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("500", "220", "null", null))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requiredBy").isNotEmpty());

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

        verifyNoInteractions(repository);
    }

    @Test
    void missingRequestReturns404() throws Exception {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Buyer request not found."));

        verify(repository).findById(id);
    }

    @Test
    void invalidUuidReturns400WithoutRepositoryCalls()
            throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid parameter"));

        verifyNoInteractions(repository);
    }

    @Test
    void listReturnsMappedPageWithStableSorting() throws Exception {
        BuyerRequest request = new BuyerRequest(
                "Demo Buyer",
                "TEST-BUYER",
                "Tomato",
                new BigDecimal("500.000"),
                "Matale",
                LocalDate.now().plusDays(7),
                new BigDecimal("220.00")
        );

        PageRequest expectedPage = PageRequest.of(
                0,
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        when(repository.findAll(expectedPage))
                .thenReturn(new PageImpl<>(
                        List.of(request),
                        expectedPage,
                        1
                ));

        mockMvc.perform(
                        get(BASE)
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].buyerName")
                        .value("Demo Buyer"))
                .andExpect(jsonPath("$.content[0].cropName")
                        .value("Tomato"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(repository).findAll(expectedPage);
    }

    private String futureDateJson() {
        return "\"" + LocalDate.now().plusDays(7) + "\"";
    }

    private String body(
            String quantity,
            String price,
            String requiredByJson,
            String blankField
    ) {
        String buyerName = "buyerName".equals(blankField)
                ? "   " : "  Demo Buyer  ";

        String phone = "phone".equals(blankField)
                ? "   " : "  TEST-BUYER  ";

        String cropName = "cropName".equals(blankField)
                ? "   " : "  Tomato  ";

        String district = "district".equals(blankField)
                ? "   " : "  Matale  ";

        return """
                {
                  "buyerName": "%s",
                  "phone": "%s",
                  "cropName": "%s",
                  "quantityKg": %s,
                  "district": "%s",
                  "requiredBy": %s,
                  "maxPricePerKg": %s
                }
                """.formatted(
                buyerName,
                phone,
                cropName,
                quantity,
                district,
                requiredByJson,
                price
        );
    }
}