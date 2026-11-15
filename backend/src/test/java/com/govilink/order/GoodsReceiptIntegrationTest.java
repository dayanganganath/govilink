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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoodsReceiptIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderApprovalService approvals;

    @Autowired
    private OrderCollectionService collections;

    private String email;
    private UUID orderId;
    private UUID harvestId;
    private UUID reservationId;
    private long version;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        email = "receipt-" + unique + "@example.com";

        AppUser staff = new AppUser(
                email,
                "Receipt Coordinator",
                "Unused password hash for authenticated test requests",
                Set.of(UserRole.COORDINATOR)
        );

        Farmer farmer = new Farmer(
                "Receipt Farmer",
                "RF-" + unique,
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
                "Receipt Buyer",
                "RB-" + unique,
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
                "Test delivery address"
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

        var approved = approvals.approve(
                quotation.getId(),
                new OrderDtos.ApproveRequest(
                        quotation.getVersion(),
                        "Receipt Buyer",
                        ApprovalChannel.PHONE,
                        "Buyer confirmed.",
                        Instant.now().minusSeconds(5)
                                .truncatedTo(ChronoUnit.MICROS)
                ),
                email
        );

        orderId = approved.id();
        harvestId = harvest.getId();
        reservationId = approved.reservations().get(0).id();

        var started = collections.start(
                orderId,
                new OrderCollectionService.StartRequest(
                        approved.version(),
                        "Pickup arranged."
                ),
                email
        );

        version = started.orderVersion();

        entityManager.clear();
    }

    @Test
    void recordsPartialReceiptAndKeepsRemainingStockReserved()
            throws Exception {
        submit(body(UUID.randomUUID(), version, "40", now()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityKg").value(40))
                .andExpect(jsonPath("$.orderStatus")
                        .value("COLLECTION_IN_PROGRESS"))
                .andExpect(jsonPath("$.orderVersion")
                        .value(version + 1));

        assertBalances("60", "40", ReservationStatus.ACTIVE);
        assertEquals(1L, receiptCount());

        mvc.perform(
                get(endpoint())
                        .with(user(email).roles("COORDINATOR"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.reservations[0].remainingQuantityKg")
                        .value(60))
                .andExpect(jsonPath("$.reservations[0].collectedQuantityKg")
                        .value(40));
    }

    @Test
    void identicalRetryDoesNotCollectStockTwice() throws Exception {
        UUID requestId = UUID.randomUUID();

        String body = body(requestId, version, "40", now());

        submit(body).andExpect(status().isCreated());
        submit(body).andExpect(status().isOk());

        assertBalances("60", "40", ReservationStatus.ACTIVE);
        assertEquals(1L, receiptCount());

        mvc.perform(
                get(endpoint() + "/" + requestId)
                        .with(user(email).roles("COORDINATOR"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.quantityKg").value(40));
    }

    @Test
    void reusedRequestIdWithChangedPayloadIsRejected() throws Exception {
        UUID requestId = UUID.randomUUID();
        Instant receivedAt = now();

        submit(body(requestId, version, "40", receivedAt))
                .andExpect(status().isCreated());

        submit(body(requestId, version, "41", receivedAt))
                .andExpect(status().isConflict());

        assertBalances("60", "40", ReservationStatus.ACTIVE);
        assertEquals(1L, receiptCount());
    }

    @Test
    void completingAllQuantitiesMarksTheOrderCollected()
            throws Exception {
        submit(body(UUID.randomUUID(), version, "40", now()))
                .andExpect(status().isCreated());

        submit(body(UUID.randomUUID(), version + 1, "60", now()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("COLLECTED"));

        assertBalances("0", "100", ReservationStatus.CONSUMED);
        assertEquals(2L, receiptCount());

        entityManager.clear();

        assertEquals(
                OrderStatus.COLLECTED,
                entityManager.find(ManagedOrder.class, orderId)
                        .getStatus()
        );
    }

    @Test
    void staleVersionCannotChangeStock() throws Exception {
        submit(body(UUID.randomUUID(), version + 1, "40", now()))
                .andExpect(status().isConflict());

        assertBalances("100", "0", ReservationStatus.ACTIVE);
        assertEquals(0L, receiptCount());
    }

    @Test
    void quantityAboveReservationBalanceIsRejected() throws Exception {
        submit(body(UUID.randomUUID(), version, "100.001", now()))
                .andExpect(status().isConflict());

        assertBalances("100", "0", ReservationStatus.ACTIVE);
        assertEquals(0L, receiptCount());
    }

    @Test
    void receiptBeforeCollectionStartedIsRejected() throws Exception {
        submit(body(
                UUID.randomUUID(),
                version,
                "40",
                now().minusSeconds(3600)
        ))
                .andExpect(status().isBadRequest());

        assertBalances("100", "0", ReservationStatus.ACTIVE);
        assertEquals(0L, receiptCount());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        String body = body(UUID.randomUUID(), version, "40", now());

        mvc.perform(
                post(endpoint())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isUnauthorized());

        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isForbidden());

        assertEquals(0L, receiptCount());
    }

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }

    private String body(
            UUID requestId,
            long requestedVersion,
            String quantity,
            Instant receivedAt
    ) {
        return """
                {
                  "requestId": "%s",
                  "version": %d,
                  "reservationId": "%s",
                  "quantityKg": %s,
                  "receivedAt": "%s",
                  "reference": "  Farmer receipt confirmed.  "
                }
                """.formatted(
                requestId,
                requestedVersion,
                reservationId,
                quantity,
                receivedAt
        );
    }

    private void assertBalances(
            String reserved,
            String collected,
            ReservationStatus expectedStatus
    ) {
        entityManager.flush();
        entityManager.clear();

        HarvestListing harvest = entityManager.find(
                HarvestListing.class,
                harvestId
        );

        OrderReservation reservation = entityManager.find(
                OrderReservation.class,
                reservationId
        );

        assertAmount("100", harvest.getQuantityKg());
        assertAmount(reserved, harvest.getReservedQuantityKg());
        assertAmount(collected, harvest.getCollectedQuantityKg());
        assertAmount("0", harvest.getAvailableQuantityKg());

        assertAmount(collected, reservation.getCollectedQuantityKg());
        assertAmount(reserved, reservation.getRemainingQuantityKg());
        assertEquals(expectedStatus, reservation.getStatus());
    }

    private long receiptCount() {
        return entityManager.createQuery(
                """
                select count(r)
                from GoodsReceipt r
                where r.order.id = :id
                """,
                Long.class
        )
                .setParameter("id", orderId)
                .getSingleResult();
    }

    private String endpoint() {
        return "/api/v1/orders/" + orderId + "/receipts";
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, amount(expected).compareTo(actual));
    }
}