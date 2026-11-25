package com.govilink.order;

import com.govilink.harvest.HarvestListing;
import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderCancellationService {

    private static final Map<String, Object> LOCK_HINTS =
            Map.of("jakarta.persistence.lock.timeout", 5000);

    private final AppUserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderCancellationService(AppUserRepository users) {
        this.users = users;
    }

    public record CancelRequest(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record Details(
            UUID id,
            UUID orderId,
            OrderStatus status,
            long orderVersion,
            OrderStatus previousStatus,
            UUID cancelledBy,
            Instant cancelledAt,
            String reason,
            BigDecimal releasedQuantityKg
    ) {
    }

    @Transactional
    public Details cancel(UUID orderId, CancelRequest request, String email) {
        AppUser actor = requireStaff(email);

        if (request == null
                || request.version() == null
                || request.version() < 0
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid order version and cancellation reason are required."
            );
        }

        ManagedOrder order = entityManager.find(
                ManagedOrder.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE,
                LOCK_HINTS
        );

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found."
            );
        }

        if (order.getVersion() != request.version()) {
            throw conflict("Order has changed. Reload before cancelling.");
        }

        if (order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.COLLECTION_IN_PROGRESS) {
            throw conflict(
                    "Only confirmed orders or orders awaiting their first goods receipt can be cancelled."
            );
        }

        long receiptCount = entityManager.createQuery(
                """
                select count(g)
                from GoodsReceipt g
                where g.order.id = :orderId
                """,
                Long.class
        )
                .setParameter("orderId", orderId)
                .getSingleResult();

        if (receiptCount != 0) {
            throw conflict(
                    "This order has received goods and cannot be cancelled."
            );
        }

        List<OrderReservation> reservations = order.getReservations().stream()
                .sorted(Comparator.comparing(OrderReservation::getId))
                .toList();

        if (reservations.isEmpty()) {
            throw conflict("The order has no reservations to release.");
        }

        // Lock reservation rows first, then shared harvest rows in ID order.
        for (OrderReservation reservation : reservations) {
            entityManager.refresh(
                    reservation,
                    LockModeType.PESSIMISTIC_WRITE,
                    LOCK_HINTS
            );
        }

        List<HarvestListing> harvests = reservations.stream()
                .map(OrderReservation::getHarvestListing)
                .distinct()
                .sorted(Comparator.comparing(HarvestListing::getId))
                .toList();

        for (HarvestListing harvest : harvests) {
            entityManager.refresh(
                    harvest,
                    LockModeType.PESSIMISTIC_WRITE,
                    LOCK_HINTS
            );
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (now.isBefore(order.getRecordedAt())) {
            throw conflict("Cancellation cannot precede order recording.");
        }

        BigDecimal released = new BigDecimal("0.000");

        for (OrderReservation reservation : reservations) {
            if (reservation.getStatus() != ReservationStatus.ACTIVE
                    || reservation.getCollectedQuantityKg().signum() != 0) {
                throw conflict(
                        "All reservations must be active with no received goods."
                );
            }

            if (now.isBefore(reservation.getReservedAt())) {
                throw conflict("Cancellation cannot precede stock reservation.");
            }

            released = released.add(reservation.getQuantityKg());
        }

        // Check the full release for each harvest before changing any balances.
        for (HarvestListing harvest : harvests) {
            BigDecimal required = reservations.stream()
                    .filter(reservation -> reservation.getHarvestListing()
                            .getId().equals(harvest.getId()))
                    .map(OrderReservation::getQuantityKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (harvest.getReservedQuantityKg().compareTo(required) < 0) {
                throw conflict(
                        "Reserved stock does not match this order. Reload and review the stock balances."
                );
            }
        }

        OrderCancellation audit = new OrderCancellation(
                order,
                actor,
                now,
                request.reason().strip(),
                released
        );

        for (OrderReservation reservation : reservations) {
            reservation.release(now);
        }

        entityManager.persist(audit);
        entityManager.flush();

        int changed = entityManager.createQuery(
                """
                update ManagedOrder o
                set o.status = :nextStatus,
                    o.version = o.version + 1,
                    o.updatedAt = :now
                where o.id = :id
                  and o.status = :expectedStatus
                  and o.version = :version
                """
        )
                .setParameter("nextStatus", OrderStatus.CANCELLED)
                .setParameter("expectedStatus", audit.getPreviousStatus())
                .setParameter("now", now)
                .setParameter("id", orderId)
                .setParameter("version", request.version())
                .executeUpdate();

        if (changed != 1) {
            throw conflict("Order has changed. Reload before cancelling.");
        }

        entityManager.refresh(order);
        return details(audit);
    }

    @Transactional(readOnly = true)
    public Details getDetails(UUID orderId, String email) {
        requireStaff(email);

        OrderCancellation audit = entityManager.createQuery(
                """
                select c
                from OrderCancellation c
                join fetch c.order
                join fetch c.cancelledBy
                where c.order.id = :orderId
                """,
                OrderCancellation.class
        )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cancellation has not been recorded for this order."
                ));

        return details(audit);
    }

    private Details details(OrderCancellation audit) {
        return new Details(
                audit.getId(),
                audit.getOrder().getId(),
                audit.getOrder().getStatus(),
                audit.getOrder().getVersion(),
                audit.getPreviousStatus(),
                audit.getCancelledBy().getId(),
                audit.getCancelledAt(),
                audit.getReason(),
                audit.getReleasedQuantityKg()
        );
    }

    private AppUser requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        AppUser actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        boolean staff = actor.getRoles().stream()
                .anyMatch(role -> role == UserRole.ADMIN
                        || role == UserRole.COORDINATOR);

        if (!actor.isActive() || !staff) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active staff access is required."
            );
        }

        return actor;
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
