package com.govilink.order;

import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderDeliveryServiceTest {

    private final UUID orderId = UUID.randomUUID();
    private final String email = "delivery-test@example.com";

    private final Instant dispatchedAt =
            Instant.parse("2020-01-01T04:00:00Z");

    private final Instant deliveredAt =
            Instant.parse("2020-01-01T05:00:00Z");

    private AppUserRepository users;
    private EntityManager entityManager;
    private ManagedOrder order;
    private AppUser actor;
    private OrderDeliveryService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        entityManager = mock(EntityManager.class);
        order = mock(ManagedOrder.class);
        actor = mock(AppUser.class);

        service = new OrderDeliveryService(users);

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );

        when(users.findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(actor));

        when(actor.isActive()).thenReturn(true);
        when(actor.getRoles())
                .thenReturn(Set.of(UserRole.COORDINATOR));
        when(actor.getId()).thenReturn(UUID.randomUUID());

        when(entityManager.find(
                eq(ManagedOrder.class),
                eq(orderId),
                eq(LockModeType.PESSIMISTIC_WRITE),
                anyMap()
        )).thenReturn(order);

        when(order.getId()).thenReturn(orderId);
        when(order.getVersion()).thenReturn(8L);
        when(order.getStatus())
                .thenReturn(OrderStatus.OUT_FOR_DELIVERY);

        when(order.getRecordedAt())
                .thenReturn(dispatchedAt.minusSeconds(60));
    }

    @Test
    void confirmsDeliveryAndRecordsTrimmedAudit() {
        dispatchTime(dispatchedAt);
        Query update = statusUpdate(1);

        doAnswer(invocation -> {
            when(order.getStatus())
                    .thenReturn(OrderStatus.DELIVERED);
            when(order.getVersion()).thenReturn(9L);
            return null;
        }).when(entityManager).refresh(order);

        var result = service.confirm(
                orderId,
                request(),
                email
        );

        assertEquals(OrderStatus.DELIVERED, result.status());
        assertEquals(9L, result.orderVersion());
        assertEquals("Test Buyer", result.receiverName());
        assertEquals("Buyer signed delivery note 42", result.reference());
        assertEquals(deliveredAt, result.deliveredAt());
        assertEquals(actor.getId(), result.recordedBy());
        assertFalse(result.recordedAt().isBefore(deliveredAt));

        var captured = org.mockito.ArgumentCaptor.forClass(
                OrderDelivery.class
        );

        verify(entityManager).persist(captured.capture());

        assertEquals(
                8L,
                captured.getValue().getOrderVersionAtDelivery()
        );

        verify(update).executeUpdate();
    }

    @Test
    void rejectsStaleVersion() {
        assertStatus(409, () -> service.confirm(
                orderId,
                new OrderDeliveryService.ConfirmRequest(
                        7L,
                        "Test Buyer",
                        deliveredAt,
                        "Delivery note"
                ),
                email
        ));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsOrderNotDispatched() {
        when(order.getStatus())
                .thenReturn(OrderStatus.COLLECTED);

        assertStatus(409, () ->
                service.confirm(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsDeliveryBeforeDispatch() {
        dispatchTime(deliveredAt.plusSeconds(60));

        assertStatus(400, () ->
                service.confirm(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsFutureDeliveryTime() {
        assertStatus(400, () -> service.confirm(
                orderId,
                new OrderDeliveryService.ConfirmRequest(
                        8L,
                        "Test Buyer",
                        Instant.now().plusSeconds(3600),
                        "Delivery note"
                ),
                email
        ));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsMissingDispatchAudit() {
        dispatchTime(null);

        assertStatus(409, () ->
                service.confirm(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsBlankReceiverName() {
        assertStatus(400, () -> service.confirm(
                orderId,
                new OrderDeliveryService.ConfirmRequest(
                        8L,
                        "   ",
                        deliveredAt,
                        "Delivery note"
                ),
                email
        ));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsInactiveStaff() {
        when(actor.isActive()).thenReturn(false);

        assertStatus(403, () ->
                service.confirm(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsAnonymousRequest() {
        assertStatus(401, () ->
                service.confirm(orderId, request(), null));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsFailedVersionTransition() {
        dispatchTime(dispatchedAt);
        statusUpdate(0);

        assertStatus(409, () ->
                service.confirm(orderId, request(), email));
    }

    private OrderDeliveryService.ConfirmRequest request() {
        return new OrderDeliveryService.ConfirmRequest(
                8L,
                "  Test Buyer  ",
                deliveredAt,
                "  Buyer signed delivery note 42  "
        );
    }

    @SuppressWarnings("unchecked")
    private void dispatchTime(Instant time) {
        TypedQuery<Instant> query = mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(),
                eq(Instant.class)
        )).thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.getResultStream()).thenAnswer(invocation ->
                time == null
                        ? Stream.<Instant>empty()
                        : Stream.of(time)
        );
    }

    private Query statusUpdate(int affectedRows) {
        Query query = mock(Query.class);

        when(entityManager.createQuery(anyString()))
                .thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.executeUpdate()).thenReturn(affectedRows);

        return query;
    }

    private void assertStatus(int expected, Runnable action) {
        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                action::run
        );

        assertEquals(expected, failure.getStatusCode().value());
    }
}