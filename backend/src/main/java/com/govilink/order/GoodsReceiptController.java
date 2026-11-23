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
@RequestMapping("/api/v1/orders/{orderId}/receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    public GoodsReceiptController(GoodsReceiptService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GoodsReceiptService.ReceiptResponse> record(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody
            GoodsReceiptService.CreateRequest request,
            Authentication authentication
    ) {
        var result = service.record(
                orderId,
                request,
                email(authentication)
        );

        if (!result.created()) {
            return ResponseEntity.ok(result.receipt());
        }

        return ResponseEntity.created(
                URI.create(
                        "/api/v1/orders/"
                                + orderId
                                + "/receipts/"
                                + result.receipt().id()
                )
        ).body(result.receipt());
    }

    @GetMapping
    public GoodsReceiptService.ReceiptPage list(
            @PathVariable("orderId") UUID orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return service.list(
                orderId,
                page,
                size,
                email(authentication)
        );
    }

    @GetMapping("/{receiptId}")
    public GoodsReceiptService.ReceiptResponse get(
            @PathVariable("orderId") UUID orderId,
            @PathVariable("receiptId") UUID receiptId,
            Authentication authentication
    ) {
        return service.get(
                orderId,
                receiptId,
                email(authentication)
        );
    }

    private String email(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return authentication.getName();
    }
}