package com.govilink.order;

import jakarta.validation.Valid;

import java.security.Principal;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderDeliveryController {

    private final OrderDeliveryService service;

    public OrderDeliveryController(OrderDeliveryService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/delivery")
    public OrderDeliveryService.Details confirm(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDeliveryService.ConfirmRequest request,
            Principal principal
    ) {
        return service.confirm(
                orderId,
                request,
                principal == null ? null : principal.getName()
        );
    }

    @GetMapping("/{orderId}/delivery")
    public OrderDeliveryService.Details getDetails(
            @PathVariable UUID orderId,
            Principal principal
    ) {
        return service.getDetails(
                orderId,
                principal == null ? null : principal.getName()
        );
    }
}