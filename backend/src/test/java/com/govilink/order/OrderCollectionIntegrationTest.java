package com.govilink.order;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.farmer.Farmer;
import com.govilink.harvest.HarvestListing;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;
import com.govilink.quotation.Quotation;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderCollectionIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderApprovalService approvals;

    @Autowired
    private OrderCollectionService collections;

    private String email;
    private UUID staffId;
    private UUID orderId;
    private UUID harvestId;
    private long version;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        email = "collection-" + unique + "@example.com";

        AppUser staff = new AppUser(
                email,
                "Collection Coordinator",
                "Unused password hash for authenticated test requests",
                Set.of(UserRole.COORDINATOR)
        );

        Farmer farmer = new Farmer(
                "Collection Test Farmer",
                "CF-" + unique,
                "Matale",
                "Test Village"
        );

        entityManager.persist(staff);
        entityManager.persist(farmer);
        entityManager.flush();

        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Colombo")
        );

        HarvestListing harvest = new HarvestListing(
                farmer,
                "Tomato",
                amount("100"),
                amount("200"),
                today
        );

        BuyerRequest buyer = new BuyerRequest(
                "Collection Test Buyer",
                "CB-" + unique,
                "Tomato",
                amount("100"),
                "Matale",
                today.plusDays(7),
                amount("250")
        );

        entityManager.persist(harvest);
        entityManager.persist(buyer);
        entityManager.flush();

        Quotation quotation = new Quotation(
                buyer,
                staff,
                amount("100"),
                amount("200"),
                "Test address"
        );

        quotation.addLine(
                harvest,
                amount("100"),
                amount("200"),
                true
        );

        entityManager.persist(quotation);
        entityManager.flush();

        quotation.issue(
                staff,
                Instant.now().minusSeconds(120)
                        .truncatedTo(ChronoUnit.MICROS),
                Instant.now().plusSeconds(3600)
                        .truncatedTo(ChronoUnit.MICROS),
                Set.of(quotation.getLines().get(0).getId())
        );

        entityManager.flush();

        var order = approvals.approve(
                quotation.getId(),
                new OrderDtos.ApproveRequest(
                        quotation.getVersion(),
                        "Collection Test Buyer",
                        ApprovalChannel.PHONE,
                        "Buyer approved the test order.",
                        Instant.now().minusSeconds(5)
                                .truncatedTo(ChronoUnit.MICROS)
                ),
                email
        );

        staffId = staff.getId();
        harvestId = harvest.getId();
        orderId = order.id();
        version = order.version();

        entityManager.clear();
    }

    @Test
    void startsCollectionAndReloadsAuditWithoutConsumingStock()
            throws Exception {
        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(version))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("COLLECTION_IN_PROGRESS"))
                .andExpect(jsonPath("$.orderVersion")
                        .value(version + 1))
                .andExpect(jsonPath("$.startedBy")
                        .value(staffId.toString()))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.reference")
                        .value("Vehicle and farmer pickup arranged."));

        entityManager.clear();

        mvc.perform(
                get("/api/v1/orders/" + orderId + "/collection")
                        .with(user(email).roles("COORDINATOR"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("COLLECTION_IN_PROGRESS"))
                .andExpect(jsonPath("$.reference")
                        .value("Vehicle and farmer pickup arranged."));

        HarvestListing harvest = entityManager.find(
                HarvestListing.class,
                harvestId
        );

        assertEquals(
                0,
                amount("100").compareTo(
                        harvest.getReservedQuantityKg()
                )
        );

        assertEquals(
                0,
                amount("0").compareTo(
                        harvest.getAvailableQuantityKg()
                )
        );

        ManagedOrder saved = entityManager.find(
                ManagedOrder.class,
                orderId
        );

        assertTrue(saved.getReservations().stream()
                .allMatch(reservation ->
                        reservation.getStatus()
                                == ReservationStatus.ACTIVE
                ));
    }

    @Test
    void staleVersionCannotStartCollection() throws Exception {
        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(version + 1))
        )
                .andExpect(status().isConflict());

        assertEquals(0L, auditCount());

        assertEquals(
                OrderStatus.CONFIRMED,
                entityManager.find(ManagedOrder.class, orderId)
                        .getStatus()
        );
    }

    @Test
    void cannotStartTheSameCollectionTwice() throws Exception {
        var first = collections.start(
                orderId,
                new OrderCollectionService.StartRequest(
                        version,
                        "First collection start."
                ),
                email
        );

        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(first.orderVersion()))
        )
                .andExpect(status().isConflict());

        assertEquals(1L, auditCount());

        assertEquals(
                "First collection start.",
                collections.getDetails(orderId, email).reference()
        );
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(
                post(endpoint())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(version))
        )
                .andExpect(status().isUnauthorized());

        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(version))
        )
                .andExpect(status().isForbidden());

        assertEquals(0L, auditCount());
    }

    @Test
    void rejectsBlankCollectionReference() throws Exception {
        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "version": %d,
                                  "reference": "   "
                                }
                                """.formatted(version)
                        )
        )
                .andExpect(status().isBadRequest());

        assertEquals(0L, auditCount());
    }

    private String endpoint() {
        return "/api/v1/orders/"
                + orderId
                + "/collection/start";
    }

    private String body(long requestedVersion) {
        return """
                {
                  "version": %d,
                  "reference": "  Vehicle and farmer pickup arranged.  "
                }
                """.formatted(requestedVersion);
    }

    private long auditCount() {
        return entityManager.createQuery(
                """
                select count(c)
                from OrderCollectionStart c
                where c.order.id = :orderId
                """,
                Long.class
        )
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}