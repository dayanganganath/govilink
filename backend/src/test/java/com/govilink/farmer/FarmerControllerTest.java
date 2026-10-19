package com.govilink.farmer;

import com.govilink.common.ApiExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FarmerControllerTest {

    private FarmerService service;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        service = mock(FarmerService.class);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FarmerController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void blankNameReturnsValidationProblemWithoutCallingService()
            throws Exception {

        String body = """
                {
                  "fullName": "",
                  "phone": "TEST-001",
                  "district": "Matale",
                  "village": "Demo Village"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/farmers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("One or more fields are invalid."))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers"))
                .andExpect(jsonPath("$.errors.fullName").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void missingFarmerReturnsNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();

        when(service.getById(id))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Farmer not found"
                ));

        mockMvc.perform(get("/api/v1/farmers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value("Farmer not found"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers/" + id));

        verify(service).getById(id);
    }

    @Test
    void malformedUuidReturnsProblemWithoutCallingService()
            throws Exception {

        mockMvc.perform(get("/api/v1/farmers/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Invalid parameter"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("Parameter 'id' has an invalid format."))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers/not-a-uuid"));

        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturnsProblemWithoutCallingService()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/farmers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Invalid request body"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "Provide a readable JSON body with valid field types."
                ))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers"));

        verifyNoInteractions(service);
    }

    @Test
    void validUpdateReturnsUpdatedFarmer() throws Exception {
        UUID id = UUID.randomUUID();

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                "Updated Village",
                0L
        );

        var response = new FarmerDtos.Response(
                id,
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                "Updated Village",
                true,
                1L,
                Instant.parse("2026-10-19T10:00:00Z"),
                Instant.parse("2026-10-19T10:05:00Z")
        );

        when(service.update(id, request)).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/farmers/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateBody(0L))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName")
                        .value("Updated Farmer"))
                .andExpect(jsonPath("$.phone").value("TEST-002"))
                .andExpect(jsonPath("$.district").value("Kandy"))
                .andExpect(jsonPath("$.village")
                        .value("Updated Village"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(1));

        verify(service).update(id, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            ", \"version\": null",
            ", \"version\": -1"
    })
    void missingNullOrNegativeVersionReturnsValidationProblem(
            String versionField
    ) throws Exception {
        UUID id = UUID.randomUUID();

        String body = """
                {
                  "fullName": "Updated Farmer",
                  "phone": "TEST-002",
                  "district": "Kandy",
                  "village": "Updated Village"
                  %s
                }
                """.formatted(versionField);

        mockMvc.perform(
                        put("/api/v1/farmers/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.version").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void staleUpdateReturnsConflictProblem() throws Exception {
        UUID id = UUID.randomUUID();

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                "Updated Village",
                0L
        );

        when(service.update(id, request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Farmer was changed by another request. "
                                + "Reload the latest details and try again."
                ));

        mockMvc.perform(
                        put("/api/v1/farmers/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateBody(0L))
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Farmer was changed by another request. "
                                + "Reload the latest details and try again."
                ))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers/" + id));

        verify(service).update(id, request);
    }

    @Test
    void databaseOptimisticLockFailureReturnsConflictProblem()
            throws Exception {
        UUID id = UUID.randomUUID();

        var request = new FarmerUpdateRequest(
                "Updated Farmer",
                "TEST-002",
                "Kandy",
                "Updated Village",
                0L
        );

        when(service.update(id, request))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        Farmer.class,
                        id
                ));

        mockMvc.perform(
                        put("/api/v1/farmers/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateBody(0L))
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Farmer was changed by another request. "
                                + "Reload the latest details and try again."
                ));

        verify(service).update(id, request);
    }

    private String validUpdateBody(long version) {
        return """
                {
                  "fullName": "Updated Farmer",
                  "phone": "TEST-002",
                  "district": "Kandy",
                  "village": "Updated Village",
                  "version": %d
                }
                """.formatted(version);
    }
}