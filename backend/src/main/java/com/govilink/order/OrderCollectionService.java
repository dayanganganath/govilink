package com.govilink.order;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderCollectionService {

    private final AppUserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderCollectionService(AppUserRepository users) {
        this.users = users;
    }

    public record StartRequest(
            @NotNull
            @PositiveOrZero
            Long version,

            @NotBlank
            @Size(max = 500)
            String reference
    ) {
    }

    public record Details(
            UUID id,
            UUID orderId,
            OrderStatus status,
            long orderVersion,
            UUID startedBy,
            Instant startedAt,
            String reference
    ) {
    }

    @Transactional
    public Details start(
            UUID orderId,
            StartRequest request,
            String email
    ) {
        AppUser actor = requireStaff(email);

        if (request == null
                || request.version() == null
                || request.version() < 0
                || request.reference() == null
                || request.reference().isBlank()
                || request.reference().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid order version and collection reference are required."
            );
        }

        ManagedOrder order = entityManager.find(
                ManagedOrder.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 5000)
        );

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found."
            );
        }

        if (order.getVersion() != request.version()) {
            throw conflict(
                    "Order has changed. Reload before starting collection."
            );
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw conflict(
                    "Collection can only start for a confirmed order."
            );
        }

        if (order.getReservations().isEmpty()
                || order.getReservations().stream().anyMatch(
                        reservation ->
                                reservation.getStatus()
                                        != ReservationStatus.ACTIVE
                )) {
            throw conflict(
                    "All order reservations must be active before collection starts."
            );
        }

        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        if (now.isBefore(order.getRecordedAt())) {
            throw conflict(
                    "Collection cannot start before the order was recorded."
            );
        }

        OrderCollectionStart audit = new OrderCollectionStart(
                order,
                actor,
                now,
                request.reference().strip()
        );

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
                .setParameter(
                        "nextStatus",
                        OrderStatus.COLLECTION_IN_PROGRESS
                )
                .setParameter(
                        "expectedStatus",
                        OrderStatus.CONFIRMED
                )
                .setParameter("now", now)
                .setParameter("id", orderId)
                .setParameter("version", request.version())
                .executeUpdate();

        if (changed != 1) {
            throw conflict(
                    "Order has changed. Reload before starting collection."
            );
        }

        entityManager.refresh(order);

        return details(audit);
    }

    @Transactional(readOnly = true)
    public Details getDetails(UUID orderId, String email) {
        requireStaff(email);

        OrderCollectionStart audit = entityManager.createQuery(
                """
                select c
                from OrderCollectionStart c
                join fetch c.order
                where c.order.id = :orderId
                """,
                OrderCollectionStart.class
        )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Collection has not been started for this order."
                ));

        return details(audit);
    }

    private Details details(OrderCollectionStart audit) {
        return new Details(
                audit.getId(),
                audit.getOrder().getId(),
                audit.getOrder().getStatus(),
                audit.getOrder().getVersion(),
                audit.getStartedBy().getId(),
                audit.getStartedAt(),
                audit.getReference()
        );
    }

    private AppUser requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        AppUser actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED
                ));

        boolean staff = actor.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (!actor.isActive() || !staff) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active staff access is required."
            );
        }

        return actor;
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                reason
        );
    }
}