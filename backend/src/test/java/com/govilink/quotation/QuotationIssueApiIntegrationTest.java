package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
class QuotationIssueApiIntegrationTest {

    private static final String PASSWORD =
            "Quotation issue test password 42!";

    private static String passwordHash;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private QuotationRepository quotations;

    @Autowired
    private BuyerRequestRepository buyers;

    @Autowired
    private HarvestRepository harvests;

    @Autowired
    private FarmerRepository farmers;

    @Autowired
    private AppUserRepository users;

    private AppUser staff;
    private HarvestListing harvest;
    private Quotation quotation;
    private UUID lineId;

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
                "issue-api-" + unique + "@example.com",
                "Test Coordinator",
                passwordHash,
                Set.of(UserRole.COORDINATOR)
        ));

        Farmer farmer = farmers.saveAndFlush(new Farmer(
                "Test Farmer",
                "IS-" + unique,
                "Matale",
                "Test Village"
        ));

        LocalDate date = LocalDate.now(
                ZoneId.of("Asia/Colombo")
        ).plusDays(7);

        harvest = harvests.saveAndFlush(new HarvestListing(
                farmer,
                "Tomato",
                amount("1000"),
                amount("180"),
                date
        ));

        BuyerRequest buyer = buyers.saveAndFlush(new BuyerRequest(
                "Test Buyer",
                "IB-" + unique,
                "Tomato",
                amount("500"),
                "Matale",
                date,
                amount("220")
        ));

        quotation = new Quotation(
                buyer,
                staff,
                amount("100"),
                amount("200"),
                null
        );

        quotation.addLine(
                harvest,
                amount("500"),
                amount("180"),
                false
        );

        quotation = quotations.saveAndFlush(quotation);
        lineId = quotation.getLines().get(0).getId();
    }

    @Test
    void issuesAndReloadsAuditWithoutReservingStock()
            throws Exception {
        MockHttpSession session = login(staff.getEmail());

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.issuedBy")
                        .value(staff.getId().toString()))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.priceConfirmationNeeded")
                        .value(false));

        UUID id = quotation.getId();
        UUID harvestId = harvest.getId();

        entityManager.clear();

        mvc.perform(
                get("/api/v1/quotations/" + id)
                        .session(session)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.lines[0].priceConfirmed")
                        .value(true));

        HarvestListing saved = harvests.findById(harvestId)
                .orElseThrow();

        assertEquals(
                0,
                amount("1000").compareTo(saved.getQuantityKg())
        );

        assertEquals(HarvestStatus.AVAILABLE, saved.getStatus());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    void staleVersionCannotOverwriteTheAudit()
            throws Exception {
        MockHttpSession session = login(staff.getEmail());

        MvcResult first = mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isOk())
                .andReturn();

        String issuedAt = JsonPath.read(
                first.getResponse().getContentAsString(),
                "$.issuedAt"
        );

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isConflict());

        UUID id = quotation.getId();
        entityManager.clear();

        mvc.perform(
                get("/api/v1/quotations/" + id)
                        .session(session)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedAt").value(issuedAt))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void cannotReissueUsingTheLatestVersion()
            throws Exception {
        MockHttpSession session = login(staff.getEmail());

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isOk());

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "1",
                                Instant.now().plusSeconds(7200),
                                lineId
                        ))
        )
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1"})
    void validatesTheVersion(String version)
            throws Exception {
        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                version,
                                Instant.now().plusSeconds(3600),
                                lineId
                        ))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void rejectsPastExpiry() throws Exception {
        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "0",
                                Instant.now().minusSeconds(60),
                                lineId
                        ))
        )
                .andExpect(status().isBadRequest());

        assertEquals(QuotationStatus.DRAFT, quotation.getStatus());
    }

    @Test
    void rejectsExpiryAfterTheRequiredDate()
            throws Exception {
        Instant expiry = quotation.getRequiredBy()
                .plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Colombo"))
                .toInstant();

        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0", expiry, lineId))
        )
                .andExpect(status().isBadRequest());

        assertNull(quotation.getIssuedAt());
        assertFalse(quotation.getLines().get(0).isPriceConfirmed());
    }

    @Test
    void rejectsDuplicateAndUnrelatedConfirmationIds()
            throws Exception {
        MockHttpSession session = login(staff.getEmail());
        Instant expiry = Instant.now().plusSeconds(3600);

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "0", expiry, lineId, lineId
                        ))
        )
                .andExpect(status().isBadRequest());

        mvc.perform(
                post(endpoint())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "0", expiry, UUID.randomUUID()
                        ))
        )
                .andExpect(status().isBadRequest());

        assertEquals(QuotationStatus.DRAFT, quotation.getStatus());
    }

    @Test
    void requiresExplicitPriceConfirmation()
            throws Exception {
        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "0",
                                Instant.now().plusSeconds(3600)
                        ))
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechecksHarvestAvailability() throws Exception {
        harvest.markSold();
        harvests.saveAndFlush(harvest);

        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isConflict());

        assertEquals(QuotationStatus.DRAFT, quotation.getStatus());
    }

    @Test
    void requiresStaffAuthenticationAndCsrf()
            throws Exception {
        mvc.perform(
                post(endpoint())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isUnauthorized());

        mvc.perform(
                post(endpoint())
                        .session(login(staff.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isForbidden());

        AppUser customer = users.saveAndFlush(new AppUser(
                "issue-customer-" + UUID.randomUUID() + "@example.com",
                "Test Buyer",
                passwordHash,
                Set.of(UserRole.BUYER)
        ));

        mvc.perform(
                post(endpoint())
                        .session(login(customer.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void missingQuotationReturnsNotFound()
            throws Exception {
        mvc.perform(
                post(
                        "/api/v1/quotations/"
                                + UUID.randomUUID()
                                + "/issue"
                )
                        .session(login(staff.getEmail()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody())
        )
                .andExpect(status().isNotFound());
    }

    private String endpoint() {
        return "/api/v1/quotations/"
                + quotation.getId()
                + "/issue";
    }

    private String validBody() {
        return body(
                "0",
                Instant.now().plusSeconds(3600),
                lineId
        );
    }

    private String body(
            String version,
            Instant expiresAt,
            UUID... ids
    ) {
        String confirmations = Arrays.stream(ids)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));

        return """
                {
                  "version": %s,
                  "expiresAt": "%s",
                  "confirmedLineIds": [%s]
                }
                """.formatted(version, expiresAt, confirmations);
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

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}