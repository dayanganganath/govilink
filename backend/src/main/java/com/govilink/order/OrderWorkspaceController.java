package com.govilink.order;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderWorkspaceController {

    private final OrderWorkspaceService service;

    public OrderWorkspaceController(
            OrderWorkspaceService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/orders")
    public OrderWorkspaceService.PageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return service.list(
                page,
                size,
                status,
                authentication.getName()
        );
    }
}