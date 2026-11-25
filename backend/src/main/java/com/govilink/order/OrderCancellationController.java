package com.govilink.order;

import jakarta.validation.Valid;

import java.security.Principal;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderCancellationController {

    private final OrderCancellationService service;

    public OrderCancellationController(OrderCancellationService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/cancellation")
    public OrderCancellationService.Details cancel(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody OrderCancellationService.CancelRequest request,
            Principal principal
    ) {
        return service.cancel(
                orderId,
                request,
                principal == null ? null : principal.getName()
        );
    }

    @GetMapping("/{orderId}/cancellation")
    public OrderCancellationService.Details getDetails(
            @PathVariable("orderId") UUID orderId,
            Principal principal
    ) {
        return service.getDetails(
                orderId,
                principal == null ? null : principal.getName()
        );
    }
}
