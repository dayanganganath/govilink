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
@RequestMapping("/api/v1/orders/{orderId}/collection")
public class OrderCollectionController {

    private final OrderCollectionService service;

    public OrderCollectionController(
            OrderCollectionService service
    ) {
        this.service = service;
    }

    @PostMapping("/start")
    public ResponseEntity<OrderCollectionService.Details> start(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody
            OrderCollectionService.StartRequest request,
            Authentication authentication
    ) {
        var result = service.start(
                orderId,
                request,
                email(authentication)
        );

        return ResponseEntity.created(
                URI.create(
                        "/api/v1/orders/"
                                + orderId
                                + "/collection"
                )
        ).body(result);
    }

    @GetMapping
    public OrderCollectionService.Details getDetails(
            @PathVariable("orderId") UUID orderId,
            Authentication authentication
    ) {
        return service.getDetails(
                orderId,
                email(authentication)
        );
    }

    private String email(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return authentication.getName();
    }
}