package com.govilink.order;

import jakarta.validation.Valid;

import java.security.Principal;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderDispatchController {

    private final OrderDispatchService service;

    public OrderDispatchController(OrderDispatchService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/dispatch")
    public OrderDispatchService.Details dispatch(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDispatchService.DispatchRequest request,
            Principal principal
    ) {
        return service.dispatch(
                orderId,
                request,
                principal == null ? null : principal.getName()
        );
    }

    @GetMapping("/{orderId}/dispatch")
    public OrderDispatchService.Details getDetails(
            @PathVariable UUID orderId,
            Principal principal
    ) {
        return service.getDetails(
                orderId,
                principal == null ? null : principal.getName()
        );
    }
}