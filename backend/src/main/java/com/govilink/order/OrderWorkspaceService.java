package com.govilink.order;

import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderWorkspaceService {

    private final AppUserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderWorkspaceService(AppUserRepository users) {
        this.users = users;
    }

    public record Summary(
            UUID id,
            UUID quotationId,
            String buyerName,
            String district,
            LocalDate requiredBy,
            OrderStatus status,
            String currency,
            BigDecimal quantityKg,
            BigDecimal total,
            Instant recordedAt
    ) {
    }

    public record PageResponse(
            List<Summary> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }

    @Transactional(readOnly = true)
    public PageResponse list(
            int page,
            int size,
            OrderStatus status,
            String staffEmail
    ) {
        requireStaff(staffEmail);

        if (page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100."
            );
        }

        String condition = status == null
                ? ""
                : " where o.status = :status";

        var countQuery = entityManager.createQuery(
                "select count(o) from ManagedOrder o" + condition,
                Long.class
        );

        var idQuery = entityManager.createQuery(
                "select o.id from ManagedOrder o"
                        + condition
                        + " order by o.recordedAt desc, o.id desc",
                UUID.class
        );

        if (status != null) {
            countQuery.setParameter("status", status);
            idQuery.setParameter("status", status);
        }

        long total = countQuery.getSingleResult();

        List<UUID> ids = idQuery
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        List<Summary> content = List.of();

        if (!ids.isEmpty()) {
            /*
             * Page order IDs first, then fetch quotation lines.
             * Collection fetch joins must not paginate the orders.
             */
            List<ManagedOrder> loaded = entityManager.createQuery(
                    """
                    select distinct o
                    from ManagedOrder o
                    join fetch o.quotation q
                    left join fetch q.lines
                    where o.id in :ids
                    """,
                    ManagedOrder.class
            )
                    .setParameter("ids", ids)
                    .getResultList();

            Map<UUID, ManagedOrder> byId = loaded.stream()
                    .collect(Collectors.toMap(
                            ManagedOrder::getId,
                            Function.identity()
                    ));

            content = ids.stream()
                    .map(byId::get)
                    .map(order -> {
                        var quotation = order.getQuotation();

                        return new Summary(
                                order.getId(),
                                quotation.getId(),
                                quotation.getBuyerName(),
                                quotation.getDistrict(),
                                quotation.getRequiredBy(),
                                order.getStatus(),
                                quotation.getCurrency(),
                                quotation.getQuantityKg(),
                                quotation.getTotal(),
                                order.getRecordedAt()
                        );
                    })
                    .toList();
        }

        int totalPages = (int) (
                total / size + (total % size == 0 ? 0 : 1)
        );

        return new PageResponse(
                content,
                page,
                size,
                total,
                totalPages,
                (long) page + 1 < totalPages,
                page > 0
        );
    }

    private void requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        var actor = users.findByEmailIgnoreCase(email)
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
    }
}