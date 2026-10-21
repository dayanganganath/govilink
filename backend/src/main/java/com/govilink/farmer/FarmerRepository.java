package com.govilink.farmer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface FarmerRepository extends
        JpaRepository<Farmer, UUID>,
        JpaSpecificationExecutor<Farmer> {
}