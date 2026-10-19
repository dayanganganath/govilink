package com.govilink.farmer;

import com.govilink.common.ApiExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

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

        String requestBody = """
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
                                .content(requestBody)
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

        mockMvc.perform(
                        get("/api/v1/farmers/{id}", id)
                )
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

        mockMvc.perform(
                        get("/api/v1/farmers/not-a-uuid")
                )
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
                .andExpect(jsonPath("$.detail")
                        .value(
                                "Provide a readable JSON body "
                                        + "with valid field types."
                        ))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/farmers"));

        verifyNoInteractions(service);
    }
}