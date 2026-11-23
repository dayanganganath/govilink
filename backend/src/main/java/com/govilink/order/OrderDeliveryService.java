package com.govilink.order;

import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
public class OrderDeliveryService {

    private final AppUserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderDeliveryService(AppUserRepository users) {
        this.users = users;
    }

    public record ConfirmRequest(
            @NotNull
            @PositiveOrZero
            Long version,

            @NotBlank
            @Size(max = 150)
            String receiverName,

            @NotNull
            @PastOrPresent
            Instant deliveredAt,

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
            String receiverName,
            Instant deliveredAt,
            UUID recordedBy,
            Instant recordedAt,
            String reference
    ) {
    }

    @Transactional
    public Details confirm(
            UUID orderId,
            ConfirmRequest request,
            String email
    ) {
        AppUser actor = requireStaff(email);

        validateRequest(request);

        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        if (request.deliveredAt().isAfter(now)) {
            throw badRequest(
                    "Delivery time cannot be in the future."
            );
        }

        Instant deliveredAt = request.deliveredAt()
                .truncatedTo(ChronoUnit.MICROS);

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
                    "Order has changed. Reload before confirming delivery."
            );
        }

        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw conflict(
                    "Delivery can only be confirmed for an order out for delivery."
            );
        }

        Instant dispatchedAt = entityManager.createQuery(
                """
                select d.dispatchedAt
                from OrderDispatch d
                where d.order.id = :orderId
                """,
                Instant.class
        )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> conflict(
                        "A dispatch record is required before delivery confirmation."
                ));

        if (deliveredAt.isBefore(dispatchedAt)
                || deliveredAt.isBefore(order.getRecordedAt())) {
            throw badRequest(
                    "Delivery time cannot be earlier than dispatch."
            );
        }

        OrderDelivery audit = new OrderDelivery(
                order,
                actor,
                request.receiverName().strip(),
                deliveredAt,
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
                .setParameter("nextStatus", OrderStatus.DELIVERED)
                .setParameter(
                        "expectedStatus",
                        OrderStatus.OUT_FOR_DELIVERY
                )
                .setParameter("now", now)
                .setParameter("id", orderId)
                .setParameter("version", request.version())
                .executeUpdate();

        if (changed != 1) {
            throw conflict(
                    "Order has changed. Reload before confirming delivery."
            );
        }

        entityManager.refresh(order);

        return details(audit);
    }

    @Transactional(readOnly = true)
    public Details getDetails(UUID orderId, String email) {
        requireStaff(email);

        OrderDelivery audit = entityManager.createQuery(
                """
                select d
                from OrderDelivery d
                join fetch d.order
                join fetch d.recordedBy
                where d.order.id = :orderId
                """,
                OrderDelivery.class
        )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No delivery confirmation has been recorded for this order."
                ));

        return details(audit);
    }

    private void validateRequest(ConfirmRequest request) {
        if (request == null
                || request.version() == null
                || request.version() < 0
                || request.receiverName() == null
                || request.receiverName().isBlank()
                || request.receiverName().length() > 150
                || request.deliveredAt() == null
                || request.reference() == null
                || request.reference().isBlank()
                || request.reference().length() > 500) {
            throw badRequest(
                    "A valid version, receiver name, delivery time and reference are required."
            );
        }
    }

    private Details details(OrderDelivery audit) {
        return new Details(
                audit.getId(),
                audit.getOrder().getId(),
                audit.getOrder().getStatus(),
                audit.getOrder().getVersion(),
                audit.getReceiverName(),
                audit.getDeliveredAt(),
                audit.getRecordedBy().getId(),
                audit.getRecordedAt(),
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

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                reason
        );
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                reason
        );
    }
}