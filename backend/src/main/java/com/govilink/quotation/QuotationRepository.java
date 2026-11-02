package com.govilink.quotation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository
        extends JpaRepository<Quotation, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "buyerRequest",
            "createdBy",
            "lines",
            "lines.harvestListing",
            "lines.harvestListing.farmer"
    })
    Optional<Quotation> findById(UUID id);
}