package com.govilink.order;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderApprovalController {

    private final OrderApprovalService service;

    public OrderApprovalController(
            OrderApprovalService service
    ) {
        this.service = service;
    }

    @PostMapping("/api/v1/quotations/{id}/approve")
    public ResponseEntity<OrderDtos.Response> approve(
            @PathVariable("id") UUID id,
            @Valid @RequestBody OrderDtos.ApproveRequest request,
            Authentication authentication
    ) {
        String email = staffEmail(authentication);

        OrderDtos.Response result = service.approve(
                id,
                request,
                email
        );

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/orders/" + result.id()
                        )
                )
                .body(result);
    }

    @GetMapping("/api/v1/orders/{id}")
    public OrderDtos.Response getById(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        return service.getById(
                id,
                staffEmail(authentication)
        );
    }

    private String staffEmail(
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return authentication.getName();
    }
}