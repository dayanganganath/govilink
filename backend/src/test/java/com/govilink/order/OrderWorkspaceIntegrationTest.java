package com.govilink.order;

import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderWorkspaceIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderWorkspaceService service;

    private String staffEmail;

    @BeforeEach
    void setUp() {
        staffEmail = "orders-list-"
                + UUID.randomUUID()
                + "@example.com";

        entityManager.persist(new AppUser(
                staffEmail,
                "Order Workspace Coordinator",
                "Unused password hash for authenticated test requests",
                Set.of(UserRole.COORDINATOR)
        ));

        entityManager.flush();
    }

    @Test
    void returnsBoundedPageAndCorrectTotal() throws Exception {
        long expected = entityManager.createQuery(
                "select count(o) from ManagedOrder o",
                Long.class
        ).getSingleResult();

        mvc.perform(
                get("/api/v1/orders")
                        .param("size", "2")
                        .with(user(staffEmail).roles("COORDINATOR"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(expected))
                .andExpect(jsonPath("$.content.length()")
                        .value(lessThanOrEqualTo(2)));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void filtersByStatusWithCorrectTotal(OrderStatus status) {
        long expected = entityManager.createQuery(
                """
                select count(o)
                from ManagedOrder o
                where o.status = :status
                """,
                Long.class
        )
                .setParameter("status", status)
                .getSingleResult();

        var result = service.list(
                0,
                100,
                status,
                staffEmail
        );

        assertEquals(expected, result.totalElements());

        assertTrue(result.content().stream()
                .allMatch(order -> order.status() == status));

        for (var summary : result.content()) {
            var saved = entityManager.find(
                    ManagedOrder.class,
                    summary.id()
            );

            assertEquals(
                    0,
                    saved.getQuotation().getTotal()
                            .compareTo(summary.total())
            );
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page=-1&size=20",
            "page=0&size=0",
            "page=0&size=101",
            "page=2147483647&size=100"
    })
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(
                get("/api/v1/orders?" + query)
                        .with(user(staffEmail).roles("COORDINATOR"))
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotAccessTheStaffWorkspace() throws Exception {
        String email = "orders-buyer-"
                + UUID.randomUUID()
                + "@example.com";

        entityManager.persist(new AppUser(
                email,
                "Test Buyer",
                "Unused password hash",
                Set.of(UserRole.BUYER)
        ));

        entityManager.flush();

        mvc.perform(
                get("/api/v1/orders")
                        .with(user(email).roles("BUYER"))
        )
                .andExpect(status().isForbidden());
    }
}