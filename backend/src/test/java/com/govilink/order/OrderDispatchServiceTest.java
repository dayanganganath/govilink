package com.govilink.order;

import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderDispatchServiceTest {

    private final UUID orderId = UUID.randomUUID();
    private final String email = "dispatch-test@example.com";

    private AppUserRepository users;
    private EntityManager entityManager;
    private ManagedOrder order;
    private OrderReservation reservation;
    private AppUser actor;
    private OrderDispatchService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        entityManager = mock(EntityManager.class);
        order = mock(ManagedOrder.class);
        reservation = mock(OrderReservation.class);
        actor = mock(AppUser.class);

        service = new OrderDispatchService(users);

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
        when(order.getVersion()).thenReturn(7L);
        when(order.getStatus()).thenReturn(OrderStatus.COLLECTED);
        when(order.getRecordedAt())
                .thenReturn(Instant.now().minusSeconds(120));

        when(order.getReservations())
                .thenReturn(List.of(reservation));

        when(reservation.getStatus())
                .thenReturn(ReservationStatus.CONSUMED);
        when(reservation.getQuantityKg())
                .thenReturn(new BigDecimal("100.000"));
        when(reservation.getCollectedQuantityKg())
                .thenReturn(new BigDecimal("100.000"));
    }

    @Test
    void dispatchesCollectedOrderAndRecordsAudit() {
        receiptTime(Instant.now().minusSeconds(60));
        Query update = statusUpdate(1);

        doAnswer(invocation -> {
            when(order.getStatus())
                    .thenReturn(OrderStatus.OUT_FOR_DELIVERY);
            when(order.getVersion()).thenReturn(8L);
            return null;
        }).when(entityManager).refresh(order);

        var result = service.dispatch(
                orderId,
                request(),
                email
        );

        assertEquals(OrderStatus.OUT_FOR_DELIVERY, result.status());
        assertEquals(8L, result.orderVersion());
        assertEquals("Driver pickup reference", result.reference());
        assertEquals(actor.getId(), result.dispatchedBy());
        assertNotNull(result.dispatchedAt());

        var captured = org.mockito.ArgumentCaptor.forClass(
                OrderDispatch.class
        );

        verify(entityManager).persist(captured.capture());

        assertEquals(
                7L,
                captured.getValue().getOrderVersionAtDispatch()
        );
        verify(update).executeUpdate();
    }

    @Test
    void rejectsStaleVersionWithoutWritingAudit() {
        assertStatus(409, () -> service.dispatch(
                orderId,
                new OrderDispatchService.DispatchRequest(
                        6L,
                        "Driver pickup reference"
                ),
                email
        ));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsOrderStillBeingCollected() {
        when(order.getStatus())
                .thenReturn(OrderStatus.COLLECTION_IN_PROGRESS);

        assertStatus(409, () ->
                service.dispatch(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsActiveReservation() {
        when(reservation.getStatus())
                .thenReturn(ReservationStatus.ACTIVE);

        assertStatus(409, () ->
                service.dispatch(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsIncompleteCollectedQuantity() {
        when(reservation.getCollectedQuantityKg())
                .thenReturn(new BigDecimal("40.000"));

        assertStatus(409, () ->
                service.dispatch(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsMissingGoodsReceipt() {
        receiptTime(null);

        assertStatus(409, () ->
                service.dispatch(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsBlankReference() {
        assertStatus(400, () -> service.dispatch(
                orderId,
                new OrderDispatchService.DispatchRequest(7L, "   "),
                email
        ));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsInactiveStaff() {
        when(actor.isActive()).thenReturn(false);

        assertStatus(403, () ->
                service.dispatch(orderId, request(), email));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsAnonymousRequest() {
        assertStatus(401, () ->
                service.dispatch(orderId, request(), null));

        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsFailedVersionTransition() {
        receiptTime(Instant.now().minusSeconds(60));
        statusUpdate(0);

        assertStatus(409, () ->
                service.dispatch(orderId, request(), email));
    }

    private OrderDispatchService.DispatchRequest request() {
        return new OrderDispatchService.DispatchRequest(
                7L,
                "  Driver pickup reference  "
        );
    }

    @SuppressWarnings("unchecked")
    private void receiptTime(Instant time) {
        TypedQuery<Instant> query = mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(),
                eq(Instant.class)
        )).thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.getSingleResult()).thenReturn(time);
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