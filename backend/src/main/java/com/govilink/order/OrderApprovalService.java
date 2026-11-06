package com.govilink.order;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestStatus;
import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;
import com.govilink.quotation.Quotation;
import com.govilink.quotation.QuotationLine;
import com.govilink.quotation.QuotationStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

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
public class OrderApprovalService {

    private static final Map<String, Object> LOCK_OPTIONS =
            Map.of("jakarta.persistence.lock.timeout", 5000);

    private final ManagedOrderRepository orders;
    private final AppUserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderApprovalService(
            ManagedOrderRepository orders,
            AppUserRepository users
    ) {
        this.orders = orders;
        this.users = users;
    }

    @Transactional
    public OrderDtos.Response approve(
            UUID quotationId,
            OrderDtos.ApproveRequest request,
            String staffEmail
    ) {
        AppUser actor = requireStaff(staffEmail);

        Quotation quotation = entityManager.find(
                Quotation.class,
                quotationId
        );

        if (quotation == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Quotation not found."
            );
        }

        /*
         * Every approval locks in the same order:
         * buyer request -> quotation -> harvests sorted by UUID.
         *
         * The buyer lock also serializes different quotations
         * competing to fulfil the same buyer request.
         */
        BuyerRequest buyer = quotation.getBuyerRequest();

        entityManager.refresh(
                buyer,
                LockModeType.PESSIMISTIC_WRITE,
                LOCK_OPTIONS
        );

        entityManager.refresh(
                quotation,
                LockModeType.PESSIMISTIC_WRITE,
                LOCK_OPTIONS
        );

        if (quotation.getVersion()
                != request.quotationVersion()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quotation has changed. Reload it before approving."
            );
        }

        if (orders.existsByQuotation_Id(quotationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An order already exists for this quotation."
            );
        }

        if (orders.existsByBuyerRequest_IdAndStatusNot(
                buyer.getId(),
                OrderStatus.CANCELLED
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This buyer request already has an active order."
            );
        }

        validateBuyerSnapshot(quotation, buyer);

        List<QuotationLine> lines = quotation.getLines()
                .stream()
                .sorted(Comparator.comparing(
                        line -> line.getHarvestListing().getId()
                ))
                .toList();

        /*
         * Refresh each harvest under a write lock.
         * This avoids using an earlier cached stock balance.
         */
        for (QuotationLine line : lines) {
            entityManager.refresh(
                    line.getHarvestListing(),
                    LockModeType.PESSIMISTIC_WRITE,
                    LOCK_OPTIONS
            );
        }

        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        ApprovalDetails approval = new ApprovalDetails(
                request.buyerContactName(),
                request.channel(),
                request.reference(),
                request.approvedAt()
                        .truncatedTo(ChronoUnit.MICROS)
        );

        /*
         * Constructor checks issued status, expiry, approval
         * timing, full allocation and confirmed line prices.
         * It creates reservation records without changing stock.
         */
        ManagedOrder order = new ManagedOrder(
                quotation,
                actor,
                approval,
                now
        );

        for (QuotationLine line : lines) {
            validateHarvest(quotation, buyer, line);

            line.getHarvestListing()
                    .reserve(line.getQuantityKg());
        }

        ManagedOrder saved = orders.saveAndFlush(order);

        /*
         * Explicit versioned status transition.
         * This runs inside the same transaction as the order
         * insert and harvest reservation updates.
         */
        int changed = entityManager.createQuery("""
                update Quotation q
                set q.status = :accepted,
                    q.version = q.version + 1,
                    q.updatedAt = :now
                where q.id = :id
                  and q.status = :issued
                  and q.version = :version
                  and q.expiresAt > :now
                """)
                .setParameter(
                        "accepted",
                        QuotationStatus.ACCEPTED
                )
                .setParameter(
                        "issued",
                        QuotationStatus.ISSUED
                )
                .setParameter("now", now)
                .setParameter("id", quotationId)
                .setParameter(
                        "version",
                        request.quotationVersion()
                )
                .executeUpdate();

        if (changed != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The quotation cannot be approved. Reload its latest status."
            );
        }

        entityManager.refresh(quotation);

        return response(saved);
    }

    @Transactional(readOnly = true)
    public OrderDtos.Response getById(
            UUID id,
            String staffEmail
    ) {
        requireStaff(staffEmail);

        ManagedOrder order = orders.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found."
                        )
                );

        return response(order);
    }

    private void validateBuyerSnapshot(
            Quotation quotation,
            BuyerRequest buyer
    ) {
        if (!quotation.getBuyerName()
                    .equals(buyer.getBuyerName())
                || !quotation.getBuyerPhone()
                    .equals(buyer.getPhone())
                || !same(
                        quotation.getDistrict(),
                        buyer.getDistrict()
                )
                || !quotation.getRequiredBy()
                    .equals(buyer.getRequiredBy())) {
            throw new IllegalStateException(
                    "Buyer details have changed. Prepare a new quotation."
            );
        }
    }

    private void validateHarvest(
            Quotation quotation,
            BuyerRequest buyer,
            QuotationLine line
    ) {
        HarvestListing harvest = line.getHarvestListing();

        // Reload farmer details before checking current eligibility.
        entityManager.refresh(harvest.getFarmer());

        if (harvest.getStatus() != HarvestStatus.AVAILABLE
                || !harvest.getFarmer().isActive()
                || !same(
                        harvest.getCropName(),
                        line.getCropName()
                )
                || !same(
                        line.getCropName(),
                        buyer.getCropName()
                )
                || !same(
                        harvest.getFarmer().getDistrict(),
                        quotation.getDistrict()
                )
                || harvest.getAvailableFrom()
                    .isAfter(quotation.getRequiredBy())) {
            throw new IllegalStateException(
                    "A harvest source is no longer eligible for this order."
            );
        }

        if (buyer.getMaxPricePerKg() != null
                && line.getFarmerPricePerKg()
                    .compareTo(buyer.getMaxPricePerKg()) > 0) {
            throw new IllegalStateException(
                    "A quoted price exceeds the buyer's current price limit."
            );
        }

        if (harvest.getAvailableQuantityKg()
                .compareTo(line.getQuantityKg()) < 0) {
            throw new IllegalStateException(
                    "Insufficient available stock. Prepare a new quotation."
            );
        }
    }

    private AppUser requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        AppUser actor = users.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED
                        )
                );

        boolean staffRole = actor.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (!actor.isActive() || !staffRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active staff access is required."
            );
        }

        return actor;
    }

    private boolean same(String first, String second) {
        return first.strip()
                .equalsIgnoreCase(second.strip());
    }

    private OrderDtos.Response response(
            ManagedOrder order
    ) {
        Quotation quotation = order.getQuotation();

        var reservations = order.getReservations()
                .stream()
                .sorted(Comparator.comparing(
                        reservation ->
                                reservation.getQuotationLine()
                                        .getLineNumber()
                ))
                .map(reservation ->
                        new OrderDtos.ReservationResponse(
                                reservation.getId(),
                                reservation.getQuotationLine().getId(),
                                reservation.getHarvestListing().getId(),
                                reservation.getHarvestListing()
                                        .getFarmer().getFullName(),
                                reservation.getQuotationLine().getCropName(),
                                reservation.getQuantityKg(),
                                reservation.getStatus()
                        )
                )
                .toList();

        return new OrderDtos.Response(
                order.getId(),
                quotation.getId(),
                order.getBuyerRequest().getId(),
                order.getStatus(),
                order.getBuyerContactName(),
                order.getApprovalChannel(),
                order.getApprovalReference(),
                order.getApprovedAt(),
                order.getRecordedAt(),
                order.getRecordedBy().getId(),
                quotation.getCurrency(),
                quotation.getQuantityKg(),
                quotation.getProduceSubtotal(),
                quotation.getServiceFee(),
                quotation.getDeliveryFee(),
                quotation.getTotal(),
                order.getVersion(),
                reservations
        );
    }
}