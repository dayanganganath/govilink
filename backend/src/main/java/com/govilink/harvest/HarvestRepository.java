package com.govilink.harvest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface HarvestRepository
        extends JpaRepository<HarvestListing, UUID>,
                JpaSpecificationExecutor<HarvestListing> {

    @Override
    @EntityGraph(attributePaths = "farmer")
    Page<HarvestListing> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "farmer")
    Page<HarvestListing> findAll(
            Specification<HarvestListing> specification,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "farmer")
    Page<HarvestListing> findByFarmer_Id(
            UUID farmerId,
            Pageable pageable
    );

    @Override
    @EntityGraph(attributePaths = "farmer")
    Optional<HarvestListing> findById(UUID id);
}