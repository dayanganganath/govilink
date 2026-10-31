package com.govilink.buyerrequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BuyerRequestRepository
        extends JpaRepository<BuyerRequest, UUID> {
}