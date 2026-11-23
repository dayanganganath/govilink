package com.govilink.order;

import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;

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
public class GoodsReceiptService {

    private final AppUserRepository users;
    private final ManagedOrderRepository orders;
    private final Validator validator;

    @PersistenceContext
    private EntityManager entityManager;

    public GoodsReceiptService(
            AppUserRepository users,
            ManagedOrderRepository orders,
            Validator validator
    ) {
        this.users = users;
        this.orders = orders;
        this.validator = validator;
    }

    public record CreateRequest(
            @NotNull UUID requestId,
            @NotNull @PositiveOrZero Long version,
            @NotNull UUID reservationId,
            @NotNull @DecimalMin("0.001")
            @Digits(integer = 9, fraction = 3)
            BigDecimal quantityKg,
            @NotNull @PastOrPresent Instant receivedAt,
            @NotBlank @Size(max = 500) String reference
    ) {
    }

    public record ReceiptResponse(
            UUID id,
            UUID orderId,
            UUID reservationId,
            BigDecimal quantityKg,
            Instant receivedAt,
            Instant recordedAt,
            UUID receivedBy,
            String reference,
            OrderStatus orderStatus,
            long orderVersion
    ) {
    }

    public record RecordResult(
            ReceiptResponse receipt,
            boolean created
    ) {
    }

    public record ReservationBalance(
            UUID id,
            String farmerName,
            String cropName,
            BigDecimal quantityKg,
            BigDecimal collectedQuantityKg,
            BigDecimal remainingQuantityKg,
            ReservationStatus status
    ) {
    }

    public record ReceiptPage(
            List<ReceiptResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious,
            OrderStatus orderStatus,
            long orderVersion,
            List<ReservationBalance> reservations
    ) {
    }

    @Transactional
    public RecordResult record(
            UUID orderId,
            CreateRequest request,
            String email
    ) {
        AppUser actor = requireStaff(email);

        if (request == null || !validator.validate(request).isEmpty()) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "Valid receipt details are required."
            );
        }

        ManagedOrder order = entityManager.find(
                ManagedOrder.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 5000)
        );

        if (order == null) {
            throw problem(HttpStatus.NOT_FOUND, "Order not found.");
        }

        BigDecimal quantity = request.quantityKg().setScale(3);
        Instant receivedAt = request.receivedAt()
                .truncatedTo(ChronoUnit.MICROS);
        String reference = request.reference().strip();

        GoodsReceipt existing = entityManager.find(
                GoodsReceipt.class,
                request.requestId()
        );

        /*
         * Check a previous receipt before checking the current
         * order version. A successful request may be retried
         * after its response was lost.
         */
        if (existing != null) {
            boolean samePayload =
                    existing.getOrder().getId().equals(orderId)
                    && existing.getReservation().getId()
                            .equals(request.reservationId())
                    && existing.getQuantityKg().compareTo(quantity) == 0
                    && existing.getReceivedAt().equals(receivedAt)
                    && existing.getReference().equals(reference)
                    && existing.getRequestedVersion() == request.version();

            if (!samePayload) {
                throw problem(
                        HttpStatus.CONFLICT,
                        "This request ID has already been used with different receipt details."
                );
            }

            return new RecordResult(response(existing), false);
        }

        if (order.getVersion() != request.version()) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "Order has changed. Reload before recording a receipt."
            );
        }

        if (order.getStatus() != OrderStatus.COLLECTION_IN_PROGRESS) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "Goods can only be received while collection is in progress."
            );
        }

        Instant startedAt = entityManager.createQuery(
                """
                select c.startedAt
                from OrderCollectionStart c
                where c.order.id = :orderId
                """,
                Instant.class
        )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> problem(
                        HttpStatus.CONFLICT,
                        "A saved collection start record is required."
                ));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (receivedAt.isBefore(startedAt) || receivedAt.isAfter(now)) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "Receipt time must be after collection started and cannot be in the future."
            );
        }

        OrderReservation reservation = order.getReservations().stream()
                .filter(value ->
                        value.getId().equals(request.reservationId())
                )
                .findFirst()
                .orElseThrow(() -> problem(
                        HttpStatus.NOT_FOUND,
                        "Reservation not found in this order."
                ));

        entityManager.refresh(
                reservation,
                LockModeType.PESSIMISTIC_WRITE
        );

        entityManager.refresh(
                reservation.getHarvestListing(),
                LockModeType.PESSIMISTIC_WRITE
        );

        try {
            reservation.collect(quantity, receivedAt);
        } catch (IllegalArgumentException exception) {
            throw problem(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw problem(HttpStatus.CONFLICT, exception.getMessage());
        }

        GoodsReceipt receipt = new GoodsReceipt(
                request.requestId(),
                order,
                reservation,
                actor,
                quantity,
                receivedAt,
                now,
                reference,
                request.version()
        );

        entityManager.persist(receipt);

        boolean complete = order.getReservations().stream()
                .allMatch(value ->
                        value.getStatus() == ReservationStatus.CONSUMED
                );

        OrderStatus nextStatus = complete
                ? OrderStatus.COLLECTED
                : OrderStatus.COLLECTION_IN_PROGRESS;

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
                .setParameter("nextStatus", nextStatus)
                .setParameter(
                        "expectedStatus",
                        OrderStatus.COLLECTION_IN_PROGRESS
                )
                .setParameter("now", now)
                .setParameter("id", orderId)
                .setParameter("version", request.version())
                .executeUpdate();

        if (changed != 1) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "Order has changed. Reload before recording a receipt."
            );
        }

        entityManager.refresh(order);

        return new RecordResult(response(receipt), true);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(
            UUID orderId,
            UUID receiptId,
            String email
    ) {
        requireStaff(email);

        GoodsReceipt receipt = entityManager.find(
                GoodsReceipt.class,
                receiptId
        );

        if (receipt == null
                || !receipt.getOrder().getId().equals(orderId)) {
            throw problem(HttpStatus.NOT_FOUND, "Receipt not found.");
        }

        return response(receipt);
    }

    @Transactional(readOnly = true)
    public ReceiptPage list(
            UUID orderId,
            int page,
            int size,
            String email
    ) {
        requireStaff(email);

        if (page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100."
            );
        }

        ManagedOrder order = orders.findById(orderId)
                .orElseThrow(() -> problem(
                        HttpStatus.NOT_FOUND,
                        "Order not found."
                ));

        long total = entityManager.createQuery(
                """
                select count(r)
                from GoodsReceipt r
                where r.order.id = :id
                """,
                Long.class
        )
                .setParameter("id", orderId)
                .getSingleResult();

        List<ReceiptResponse> content = entityManager.createQuery(
                """
                select r
                from GoodsReceipt r
                where r.order.id = :id
                order by r.recordedAt desc, r.id desc
                """,
                GoodsReceipt.class
        )
                .setParameter("id", orderId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(this::response)
                .toList();

        List<ReservationBalance> balances = order.getReservations()
                .stream()
                .sorted(Comparator.comparing(value ->
                        value.getQuotationLine().getLineNumber()
                ))
                .map(value -> new ReservationBalance(
                        value.getId(),
                        value.getHarvestListing().getFarmer().getFullName(),
                        value.getQuotationLine().getCropName(),
                        value.getQuantityKg(),
                        value.getCollectedQuantityKg(),
                        value.getRemainingQuantityKg(),
                        value.getStatus()
                ))
                .toList();

        int totalPages = (int) (
                total / size + (total % size == 0 ? 0 : 1)
        );

        return new ReceiptPage(
                content,
                page,
                size,
                total,
                totalPages,
                (long) page + 1 < totalPages,
                page > 0,
                order.getStatus(),
                order.getVersion(),
                balances
        );
    }

    private ReceiptResponse response(GoodsReceipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getOrder().getId(),
                receipt.getReservation().getId(),
                receipt.getQuantityKg(),
                receipt.getReceivedAt(),
                receipt.getRecordedAt(),
                receipt.getReceivedBy().getId(),
                receipt.getReference(),
                receipt.getOrder().getStatus(),
                receipt.getOrder().getVersion()
        );
    }

    private AppUser requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw problem(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        }

        AppUser actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> problem(
                        HttpStatus.UNAUTHORIZED,
                        "Sign in is required."
                ));

        boolean staff = actor.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (!actor.isActive() || !staff) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "Active staff access is required."
            );
        }

        return actor;
    }

    private ResponseStatusException problem(
            HttpStatus status,
            String reason
    ) {
        return new ResponseStatusException(status, reason);
    }
}