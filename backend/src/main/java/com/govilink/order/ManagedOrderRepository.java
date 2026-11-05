package com.govilink.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedOrderRepository
        extends JpaRepository<ManagedOrder, UUID> {

    boolean existsByQuotation_Id(UUID quotationId);

    boolean existsByBuyerRequest_IdAndStatusNot(
            UUID buyerRequestId,
            OrderStatus status
    );

    @Override
    @EntityGraph(attributePaths = {
            "quotation",
            "buyerRequest",
            "recordedBy",
            "reservations",
            "reservations.quotationLine",
            "reservations.harvestListing",
            "reservations.harvestListing.farmer"
    })
    Optional<ManagedOrder> findById(UUID id);
}