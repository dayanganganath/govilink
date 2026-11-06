package com.govilink.order;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.farmer.Farmer;
import com.govilink.harvest.HarvestListing;
import com.govilink.identity.AppUser;
import com.govilink.identity.IdentityPasswordConfig;
import com.govilink.identity.UserRole;
import com.govilink.quotation.Quotation;
import com.govilink.quotation.QuotationStatus;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApprovalIntegrationTest {

    private static final String PASSWORD =
            "Order approval test password 42!";

    private static String passwordHash;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrderApprovalService service;

    @Autowired
    private MockMvc mvc;

    private TransactionTemplate transactions;

    private UUID staffId;
    private UUID farmerId;
    private UUID harvestId;
    private String staffEmail;

    private final List<UUID> buyerIds = new ArrayList<>();

    private Quote quote;

    private record Quote(UUID id, long version) {
    }

    @BeforeAll
    static void encodePassword() {
        passwordHash = new IdentityPasswordConfig()
                .passwordEncoder()
                .encode(PASSWORD);
    }

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        transactions.executeWithoutResult(transaction -> {
            String unique = UUID.randomUUID()
                    .toString()
                    .substring(0, 8);

            staffEmail =
                    "order-test-" + unique + "@example.com";

            AppUser staff = new AppUser(
                    staffEmail,
                    "Order Test Coordinator",
                    passwordHash,
                    Set.of(UserRole.COORDINATOR)
            );

            entityManager.persist(staff);

            Farmer farmer = new Farmer(
                    "Order Test Farmer",
                    "OT-" + unique,
                    "Matale",
                    "Test Village"
            );

            entityManager.persist(farmer);
            entityManager.flush();

            HarvestListing harvest = new HarvestListing(
                    farmer,
                    "Tomato",
                    amount("100"),
                    amount("200"),
                    today()
            );

            entityManager.persist(harvest);
            entityManager.flush();

            staffId = staff.getId();
            farmerId = farmer.getId();
            harvestId = harvest.getId();

            quote = createQuote(
                    List.of(harvest),
                    amount("100")
            );
        });
    }

    @AfterEach
    void cleanUpOnlyThisTestsData() {
        if (staffId == null) {
            return;
        }

        transactions.executeWithoutResult(transaction -> {
            delete(
                    """
                    DELETE FROM order_reservations
                    WHERE order_id IN (
                        SELECT id FROM managed_orders
                        WHERE recorded_by = :id
                    )
                    """,
                    staffId
            );

            delete(
                    "DELETE FROM managed_orders WHERE recorded_by = :id",
                    staffId
            );

            delete(
                    """
                    DELETE FROM quotation_lines
                    WHERE quotation_id IN (
                        SELECT id FROM quotations
                        WHERE created_by = :id
                    )
                    """,
                    staffId
            );

            delete(
                    "DELETE FROM quotations WHERE created_by = :id",
                    staffId
            );

            for (UUID buyerId : buyerIds) {
                delete(
                        "DELETE FROM buyer_requests WHERE id = :id",
                        buyerId
                );
            }

            delete(
                    "DELETE FROM harvest_listings WHERE farmer_id = :id",
                    farmerId
            );

            delete(
                    "DELETE FROM farmers WHERE id = :id",
                    farmerId
            );

            delete(
                    "DELETE FROM app_user_roles WHERE user_id = :id",
                    staffId
            );

            delete(
                    "DELETE FROM app_users WHERE id = :id",
                    staffId
            );
        });
    }

    @Test
    void approvalCreatesAnOrderAndReservesStock() throws Exception {
        MockHttpSession session = login();

        String response = mvc.perform(
                post(endpoint(quote))
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(quote.version()))
        )
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.quotationId")
                        .value(quote.id().toString()))
                .andExpect(jsonPath("$.approvalChannel")
                        .value("PHONE"))
                .andExpect(jsonPath("$.recordedBy")
                        .value(staffId.toString()))
                .andExpect(jsonPath("$.total").value(23000))
                .andExpect(jsonPath("$.reservations.length()").value(1))
                .andExpect(jsonPath("$.reservations[0].status")
                        .value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = JsonPath.read(response, "$.id");

        mvc.perform(
                get("/api/v1/orders/" + orderId)
                        .session(session)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.total").value(23000));

        assertStock(harvestId, "100", "0");
        assertQuotationStatus(quote.id(), QuotationStatus.ACCEPTED);

        assertEquals(1L, orderCount());
    }

    @Test
    void duplicateApprovalDoesNotReserveStockTwice() {
        approve(quote);

        assertThrows(
                RuntimeException.class,
                () -> approve(quote)
        );

        assertStock(harvestId, "100", "0");
        assertEquals(1L, orderCount());
    }

    @Test
    void staleVersionCreatesNoOrderOrReservation() throws Exception {
        mvc.perform(
                post(endpoint(quote))
                        .session(login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(quote.version() + 1))
        )
                .andExpect(status().isConflict());

        assertStock(harvestId, "0", "100");
        assertQuotationStatus(quote.id(), QuotationStatus.ISSUED);
        assertEquals(0L, orderCount());
    }

    @Test
    void missingCsrfCannotCreateAnOrder() throws Exception {
        mvc.perform(
                post(endpoint(quote))
                        .session(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(quote.version()))
        )
                .andExpect(status().isForbidden());

        assertStock(harvestId, "0", "100");
        assertEquals(0L, orderCount());
    }

    @Test
    void anonymousApprovalIsRejected() throws Exception {
        mvc.perform(
                post(endpoint(quote))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(quote.version()))
        )
                .andExpect(status().isUnauthorized());

        assertEquals(0L, orderCount());
    }

    @Test
    void futureApprovalTimeIsRejected() throws Exception {
        String invalidBody = body(quote.version())
                .replace(
                        approvalTime().toString(),
                        Instant.now().plusSeconds(3600)
                                .truncatedTo(ChronoUnit.SECONDS)
                                .toString()
                );

        // Build explicitly so the test does not depend on clock timing.
        invalidBody = """
                {
                  "quotationVersion": %d,
                  "buyerContactName": "Test Buyer",
                  "channel": "PHONE",
                  "reference": "Buyer confirmed by telephone.",
                  "approvedAt": "%s"
                }
                """.formatted(
                quote.version(),
                Instant.now().plusSeconds(3600)
        );

        mvc.perform(
                post(endpoint(quote))
                        .session(login())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
        )
                .andExpect(status().isBadRequest());

        assertStock(harvestId, "0", "100");
        assertEquals(0L, orderCount());
    }

    @Test
    void failureOnSecondSourceRollsBackTheFirstReservation() {
        UUID[] sources = new UUID[2];

        Quote multiSource = transactions.execute(transaction -> {
            Farmer farmer = entityManager.find(
                    Farmer.class,
                    farmerId
            );

            HarvestListing extra = new HarvestListing(
                    farmer,
                    "Tomato",
                    amount("100"),
                    amount("200"),
                    today()
            );

            entityManager.persist(extra);
            entityManager.flush();

            HarvestListing original = entityManager.find(
                    HarvestListing.class,
                    harvestId
            );

            List<HarvestListing> sorted = List.of(original, extra)
                    .stream()
                    .sorted(Comparator.comparing(HarvestListing::getId))
                    .toList();

            Quote created = createQuote(
                    sorted,
                    amount("50")
            );

            sources[0] = sorted.get(0).getId();
            sources[1] = sorted.get(1).getId();

            // Stock becomes unavailable after the quotation was issued.
            sorted.get(1).reserve(amount("100"));

            return created;
        });

        assertNotNull(multiSource);

        assertThrows(
                RuntimeException.class,
                () -> approve(multiSource)
        );

        // The first source must remain unreserved after rollback.
        assertStock(sources[0], "0", "100");
        assertStock(sources[1], "100", "0");

        assertQuotationStatus(
                multiSource.id(),
                QuotationStatus.ISSUED
        );

        assertEquals(0L, orderCount());
    }

    @Test
    void competingOrdersCannotOverbookTheSameHarvest() throws Exception {
        List<Quote> competing = transactions.execute(transaction -> {
            HarvestListing harvest = entityManager.find(
                    HarvestListing.class,
                    harvestId
            );

            return List.of(
                    createQuote(List.of(harvest), amount("60")),
                    createQuote(List.of(harvest), amount("60"))
            );
        });

        assertNotNull(competing);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(
                    () -> raceApproval(competing.get(0), ready, start)
            );

            var second = executor.submit(
                    () -> raceApproval(competing.get(1), ready, start)
            );

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both approval workers must be ready."
            );

            start.countDown();

            int successes = first.get(30, TimeUnit.SECONDS)
                    + second.get(30, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    successes,
                    "Only one 60 kg order can reserve a 100 kg harvest."
            );

            assertStock(harvestId, "60", "40");
            assertEquals(1L, orderCount());

            long accepted = transactions.execute(transaction ->
                    entityManager.createQuery(
                                    """
                                    SELECT COUNT(q)
                                    FROM Quotation q
                                    WHERE q.createdBy.id = :staff
                                      AND q.status = :status
                                    """,
                                    Long.class
                            )
                            .setParameter("staff", staffId)
                            .setParameter(
                                    "status",
                                    QuotationStatus.ACCEPTED
                            )
                            .getSingleResult()
            );

            assertEquals(1L, accepted);
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS)
            );
        }
    }

    private int raceApproval(
            Quote candidate,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Approval start timed out.");
        }

        try {
            approve(candidate);
            return 1;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void approve(Quote candidate) {
        service.approve(
                candidate.id(),
                new OrderDtos.ApproveRequest(
                        candidate.version(),
                        "Test Buyer",
                        ApprovalChannel.PHONE,
                        "Buyer confirmed by telephone.",
                        approvalTime()
                ),
                staffEmail
        );
    }

    private Quote createQuote(
            List<HarvestListing> sources,
            BigDecimal quantityPerSource
    ) {
        AppUser staff = entityManager.find(
                AppUser.class,
                staffId
        );

        String unique = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        BuyerRequest buyer = new BuyerRequest(
                "Order Test Buyer",
                "OB-" + unique,
                "Tomato",
                quantityPerSource.multiply(
                        BigDecimal.valueOf(sources.size())
                ),
                "Matale",
                today().plusDays(7),
                amount("250")
        );

        entityManager.persist(buyer);
        entityManager.flush();

        buyerIds.add(buyer.getId());

        Quotation quotation = new Quotation(
                buyer,
                staff,
                amount("1000"),
                amount("2000"),
                "Test delivery address"
        );

        for (HarvestListing source : sources) {
            quotation.addLine(
                    source,
                    quantityPerSource,
                    amount("200"),
                    true
            );
        }

        entityManager.persist(quotation);
        entityManager.flush();

        Set<UUID> lineIds = quotation.getLines()
                .stream()
                .map(line -> line.getId())
                .collect(Collectors.toSet());

        quotation.issue(
                staff,
                Instant.now().minusSeconds(120)
                        .truncatedTo(ChronoUnit.MICROS),
                Instant.now().plusSeconds(3600)
                        .truncatedTo(ChronoUnit.MICROS),
                lineIds
        );

        entityManager.flush();

        return new Quote(
                quotation.getId(),
                quotation.getVersion()
        );
    }

    private void assertStock(
            UUID id,
            String reserved,
            String available
    ) {
        transactions.executeWithoutResult(transaction -> {
            HarvestListing harvest = entityManager.find(
                    HarvestListing.class,
                    id
            );

            assertEquals(
                    0,
                    amount(reserved).compareTo(
                            harvest.getReservedQuantityKg()
                    )
            );

            assertEquals(
                    0,
                    amount(available).compareTo(
                            harvest.getAvailableQuantityKg()
                    )
            );

            assertEquals(
                    0,
                    amount("100").compareTo(harvest.getQuantityKg()),
                    "Reservation must not overwrite the original quantity."
            );
        });
    }

    private void assertQuotationStatus(
            UUID id,
            QuotationStatus expected
    ) {
        transactions.executeWithoutResult(transaction ->
                assertEquals(
                        expected,
                        entityManager.find(Quotation.class, id)
                                .getStatus()
                )
        );
    }

    private long orderCount() {
        Long count = transactions.execute(transaction ->
                entityManager.createQuery(
                                """
                                SELECT COUNT(o)
                                FROM ManagedOrder o
                                WHERE o.recordedBy.id = :staff
                                """,
                                Long.class
                        )
                        .setParameter("staff", staffId)
                        .getSingleResult()
        );

        assertNotNull(count);
        return count;
    }

    private MockHttpSession login() throws Exception {
        var result = mvc.perform(
                post("/api/v1/auth/login")
                        .with(csrf())
                        .param("email", staffEmail)
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

    private String endpoint(Quote candidate) {
        return "/api/v1/quotations/"
                + candidate.id()
                + "/approve";
    }

    private String body(long version) {
        return """
                {
                  "quotationVersion": %d,
                  "buyerContactName": "Test Buyer",
                  "channel": "PHONE",
                  "reference": "Buyer confirmed by telephone.",
                  "approvedAt": "%s"
                }
                """.formatted(version, approvalTime());
    }

    private Instant approvalTime() {
        return Instant.now().minusSeconds(5)
                .truncatedTo(ChronoUnit.SECONDS);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Colombo"));
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void delete(String sql, UUID id) {
        entityManager.createNativeQuery(sql)
                .setParameter("id", id)
                .executeUpdate();
    }
}