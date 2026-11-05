package com.govilink.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReservationRepository
        extends JpaRepository<OrderReservation, UUID> {

    List<OrderReservation> findByOrder_Id(UUID orderId);
}