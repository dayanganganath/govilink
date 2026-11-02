package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestRepository;
import com.govilink.harvest.HarvestStatus;
import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.IdentityPasswordConfig;
import com.govilink.identity.UserRole;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuotationApiIntegrationTest {

    private static final String PASSWORD =
            "Quotation test password 42!";

    private static String passwordHash;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FarmerRepository farmers;

    @Autowired
    private HarvestRepository harvests;

    @Autowired
    private BuyerRequestRepository buyers;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private QuotationRepository quotations;

    private AppUser staff;
    private BuyerRequest buyer;
    private HarvestListing harvest;

    @BeforeAll
    static void encodePassword() {
        passwordHash = new IdentityPasswordConfig()
                .passwordEncoder()
                .encode(PASSWORD);
    }

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        staff = users.saveAndFlush(new AppUser(
                "quote-api-" + unique + "@example.com",
                "Test Coordinator",
                passwordHash,
                Set.of(UserRole.COORDINATOR)
        ));

        Farmer farmer = farmers.saveAndFlush(new Farmer(
                "Quote API Farmer",
                "QF-" + unique,
                "Matale",
                "Test Village"
        ));

        LocalDate date = LocalDate.now().plusDays(7);

        harvest = harvests.saveAndFlush(new HarvestListing(
                farmer,
                "Tomato",
                new BigDecimal("1000.000"),
                new BigDecimal("180.00"),
                date
        ));

        buyer = buyers.saveAndFlush(new BuyerRequest(
                "Quote API Buyer",
                "QB-" + unique,
                "Tomato",
                new BigDecimal("500.000"),
                "Matale",
                date,
                new BigDecimal("220.00")
        ));
    }

    @Test
    void createsAndReloadsDraftWithoutReservingStock()
            throws Exception {
        MockHttpSession session = login(staff.getEmail());

        MvcResult result = mvc.perform(
                        post("/api/v1/quotations")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(harvest.getId(), "500.000")
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy")
                        .value(staff.getId().toString()))
                .andExpect(jsonPath("$.total").value(97000))
                .andExpect(jsonPath("$.priceConfirmationNeeded")
                        .value(true))
                .andExpect(jsonPath("$.lines[0].id").isNotEmpty())
                .andReturn();

        String id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );

        assertEquals(
                "/api/v1/quotations/" + id,
                result.getResponse().getHeader("Location")
        );

        UUID harvestId = harvest.getId();
        entityManager.clear();

        mvc.perform(
                        get("/api/v1/quotations/" + id)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerName")
                        .value("Quote API Buyer"))
                .andExpect(jsonPath("$.total").value(97000))
                .andExpect(jsonPath("$.lines[0].lineSubtotal")
                        .value(90000))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        HarvestListing saved = harvests.findById(harvestId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1000.000")
                        .compareTo(saved.getQuantityKg())
        );

        assertEquals(
                HarvestStatus.AVAILABLE,
                saved.getStatus()
        );

        assertEquals(0L, saved.getVersion());
    }

    @Test
    void validatesNestedLineQuantityBeforeSaving()
            throws Exception {
        long before = quotations.count();

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(login(staff.getEmail()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(harvest.getId(), "0")
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        assertEquals(before, quotations.count());
    }

    @Test
    void rejectsMissingBuyerAndHarvest() throws Exception {
        MockHttpSession session = login(staff.getEmail());

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        UUID.randomUUID(),
                                        line(harvest.getId(), "500")
                                ))
                )
                .andExpect(status().isNotFound());

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(UUID.randomUUID(), "500")
                                ))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateSourcesWithoutSavingPartialDraft()
            throws Exception {
        long before = quotations.count();
        String source = line(harvest.getId(), "100");

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(login(staff.getEmail()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        source + "," + source
                                ))
                )
                .andExpect(status().isBadRequest());

        assertEquals(before, quotations.count());
    }

    @Test
    void rejectsQuantityAboveBuyerDemand() throws Exception {
        mvc.perform(
                        post("/api/v1/quotations")
                                .session(login(staff.getEmail()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(harvest.getId(), "501")
                                ))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsClosedBuyerRequest() throws Exception {
        ReflectionTestUtils.setField(
                buyer,
                "status",
                BuyerRequestStatus.CANCELLED
        );

        buyers.saveAndFlush(buyer);

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(login(staff.getEmail()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(harvest.getId(), "500")
                                ))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        String request = body(
                buyer.getId(),
                line(harvest.getId(), "500")
        );

        mvc.perform(
                        post("/api/v1/quotations")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(login(staff.getEmail()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void buyerAccountCannotAccessInternalQuotationApis()
            throws Exception {
        AppUser customer = users.saveAndFlush(new AppUser(
                "customer-" + UUID.randomUUID() + "@example.com",
                "Test Buyer",
                passwordHash,
                Set.of(UserRole.BUYER)
        ));

        MockHttpSession session = login(customer.getEmail());

        mvc.perform(
                        get("/api/v1/quotations/" + UUID.randomUUID())
                                .session(session)
                )
                .andExpect(status().isForbidden());

        mvc.perform(
                        post("/api/v1/quotations")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(
                                        buyer.getId(),
                                        line(harvest.getId(), "500")
                                ))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void missingQuotationReturnsNotFound() throws Exception {
        mvc.perform(
                        get("/api/v1/quotations/" + UUID.randomUUID())
                                .session(login(staff.getEmail()))
                )
                .andExpect(status().isNotFound());
    }

    private MockHttpSession login(String email)
            throws Exception {
        MvcResult result = mvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .param("email", email)
                                .param("password", PASSWORD)
                )
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest()
                        .getSession(false);

        assertNotNull(session);

        return session;
    }

    private String body(UUID buyerId, String lines) {
        return """
                {
                  "buyerRequestId": "%s",
                  "serviceFee": 4500.00,
                  "deliveryFee": 2500.00,
                  "deliveryAddress": "Test Address",
                  "lines": [%s]
                }
                """.formatted(buyerId, lines);
    }

    private String line(UUID harvestId, String quantity) {
        return """
                {
                  "harvestListingId": "%s",
                  "quantityKg": %s,
                  "farmerPricePerKg": 180.00,
                  "priceConfirmed": false   
                }
                """.formatted(harvestId, quantity);
    }
}