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
class OrderCancellationIntegrationTest {

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
    private UUID reservationId;
    private long version;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        email = "cancellation-" + unique + "@example.com";

        AppUser staff = new AppUser(
                email,
                "Cancellation Coordinator",
                "Unused password hash for authenticated test requests",
                Set.of(UserRole.COORDINATOR)
        );

        Farmer farmer = new Farmer(
                "Cancellation Farmer",
                "CF-" + unique,
                "Matale",
                "Test Village"
        );

        entityManager.persist(staff);
        entityManager.persist(farmer);
        entityManager.flush();

        staffId = staff.getId();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));

        HarvestListing harvest = new HarvestListing(
                farmer,
                "Tomato",
                amount("100"),
                amount("200"),
                today
        );

        BuyerRequest buyer = new BuyerRequest(
                "Cancellation Buyer",
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
                now().minusSeconds(120),
                now().plusSeconds(3600),
                Set.of(quotation.getLines().get(0).getId())
        );

        entityManager.flush();

        var approved = approvals.approve(
                quotation.getId(),
                new OrderDtos.ApproveRequest(
                        quotation.getVersion(),
                        "Cancellation Buyer",
                        ApprovalChannel.PHONE,
                        "Buyer confirmed.",
                        now().minusSeconds(5)
                ),
                email
        );

        orderId = approved.id();
        harvestId = harvest.getId();
        reservationId = approved.reservations().get(0).id();
        version = approved.version();

        entityManager.clear();
    }

    @Test
    void cancelsConfirmedOrderAndReleasesStockWithAudit() throws Exception {
        submit(version, "  Buyer withdrew the order.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.previousStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.orderVersion").value(version + 1))
                .andExpect(jsonPath("$.cancelledBy").value(staffId.toString()))
                .andExpect(jsonPath("$.reason").value("Buyer withdrew the order."))
                .andExpect(jsonPath("$.releasedQuantityKg").value(100))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        assertState(
                OrderStatus.CANCELLED,
                ReservationStatus.RELEASED,
                "0", "0", "100",
                version + 1
        );

        assertEquals(1L, cancellationCount());

        OrderReservation reservation =
                entityManager.find(OrderReservation.class, reservationId);

        assertNotNull(reservation.getFinishedAt());
        assertFalse(reservation.getFinishedAt()
                .isBefore(reservation.getReservedAt()));

        mvc.perform(
                get(endpoint())
                        .with(user(email).roles("COORDINATOR"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Buyer withdrew the order."))
                .andExpect(jsonPath("$.releasedQuantityKg").value(100))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelsCollectionBeforeAnyGoodsAreReceived() throws Exception {
        startCollection();

        submit(version, "Pickup cancelled before receiving goods.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus")
                        .value("COLLECTION_IN_PROGRESS"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertState(
                OrderStatus.CANCELLED,
                ReservationStatus.RELEASED,
                "0", "0", "100",
                version + 1
        );

        assertEquals(1L, cancellationCount());
    }

    @Test
    void staleVersionCannotReleaseStockOrCreateAudit() throws Exception {
        submit(version + 1, "Buyer cancelled.")
                .andExpect(status().isConflict());

        assertState(
                OrderStatus.CONFIRMED,
                ReservationStatus.ACTIVE,
                "100", "0", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void partialReceiptPreventsCancellationAndPreservesBalances()
            throws Exception {
        startCollection();
        recordReceipt("40");

        submit(version, "Buyer cancelled after partial collection.")
                .andExpect(status().isConflict());

        assertState(
                OrderStatus.COLLECTION_IN_PROGRESS,
                ReservationStatus.ACTIVE,
                "60", "40", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void collectedOrderCannotBeCancelled() throws Exception {
        startCollection();
        recordReceipt("100");

        submit(version, "Buyer cancelled after collection.")
                .andExpect(status().isConflict());

        assertState(
                OrderStatus.COLLECTED,
                ReservationStatus.CONSUMED,
                "0", "100", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void repeatedCancellationCannotReleaseStockTwice() throws Exception {
        submit(version, "Buyer cancelled.")
                .andExpect(status().isOk());

        // Use the latest version to exercise the cancelled-status guard.
        submit(version + 1, "Buyer cancelled again.")
                .andExpect(status().isConflict());

        assertState(
                OrderStatus.CANCELLED,
                ReservationStatus.RELEASED,
                "0", "0", "100",
                version + 1
        );

        assertEquals(1L, cancellationCount());
    }

    @Test
    void blankReasonIsRejectedWithoutChangingStock() throws Exception {
        submit(version, "   ")
                .andExpect(status().isBadRequest());

        assertState(
                OrderStatus.CONFIRMED,
                ReservationStatus.ACTIVE,
                "100", "0", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void reasonAboveMaximumLengthIsRejected() throws Exception {
        submit(version, "x".repeat(501))
                .andExpect(status().isBadRequest());

        assertState(
                OrderStatus.CONFIRMED,
                ReservationStatus.ACTIVE,
                "100", "0", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void negativeVersionIsRejected() throws Exception {
        submit(-1, "Buyer cancelled.")
                .andExpect(status().isBadRequest());

        assertState(
                OrderStatus.CONFIRMED,
                ReservationStatus.ACTIVE,
                "100", "0", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        String payload = body(version, "Buyer cancelled.");

        mvc.perform(
                post(endpoint())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
        )
                .andExpect(status().isUnauthorized());

        mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
        )
                .andExpect(status().isForbidden());

        assertState(
                OrderStatus.CONFIRMED,
                ReservationStatus.ACTIVE,
                "100", "0", "0",
                version
        );

        assertEquals(0L, cancellationCount());
    }

    private void startCollection() {
        var started = collections.start(
                orderId,
                new OrderCollectionService.StartRequest(
                        version,
                        "Pickup arranged."
                ),
                email
        );

        version = started.orderVersion();
        entityManager.clear();
    }

    private void recordReceipt(String quantity) throws Exception {
        String payload = """
                {
                  "requestId": "%s",
                  "version": %d,
                  "reservationId": "%s",
                  "quantityKg": %s,
                  "receivedAt": "%s",
                  "reference": "Goods received before cancellation attempt."
                }
                """.formatted(
                UUID.randomUUID(),
                version,
                reservationId,
                quantity,
                now()
        );

        mvc.perform(
                post("/api/v1/orders/" + orderId + "/receipts")
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderVersion").value(version + 1));

        version++;
        entityManager.clear();
    }

    private ResultActions submit(long requestedVersion, String reason)
            throws Exception {
        return mvc.perform(
                post(endpoint())
                        .with(user(email).roles("COORDINATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestedVersion, reason))
        );
    }

    private String body(long requestedVersion, String reason) {
        // Test reasons contain no JSON escape characters.
        return """
                {
                  "version": %d,
                  "reason": "%s"
                }
                """.formatted(requestedVersion, reason);
    }

    private void assertState(
            OrderStatus orderStatus,
            ReservationStatus reservationStatus,
            String reserved,
            String collected,
            String available,
            long expectedVersion
    ) {
        entityManager.flush();
        entityManager.clear();

        ManagedOrder order =
                entityManager.find(ManagedOrder.class, orderId);

        HarvestListing harvest =
                entityManager.find(HarvestListing.class, harvestId);

        OrderReservation reservation =
                entityManager.find(OrderReservation.class, reservationId);

        assertEquals(orderStatus, order.getStatus());
        assertEquals(expectedVersion, order.getVersion());

        assertAmount("100", harvest.getQuantityKg());
        assertAmount(reserved, harvest.getReservedQuantityKg());
        assertAmount(collected, harvest.getCollectedQuantityKg());
        assertAmount(available, harvest.getAvailableQuantityKg());

        assertEquals(reservationStatus, reservation.getStatus());
        assertAmount("100", reservation.getQuantityKg());
        assertAmount(collected, reservation.getCollectedQuantityKg());

        assertAmount(
                reservationStatus == ReservationStatus.ACTIVE
                        ? reserved : "0",
                reservation.getRemainingQuantityKg()
        );
    }

    private long cancellationCount() {
        return entityManager.createQuery(
                """
                select count(c)
                from OrderCancellation c
                where c.order.id = :orderId
                """,
                Long.class
        )
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private String endpoint() {
        return "/api/v1/orders/" + orderId + "/cancellation";
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, amount(expected).compareTo(actual));
    }
}