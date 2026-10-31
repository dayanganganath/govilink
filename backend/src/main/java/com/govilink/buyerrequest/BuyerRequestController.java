package com.govilink.buyerrequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buyer-requests")
public class BuyerRequestController {

    private final BuyerRequestService service;

    public BuyerRequestController(BuyerRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuyerRequestDtos.Response create(
            @Valid @RequestBody BuyerRequestDtos.CreateRequest request
    ) {
        return service.create(request);
    }

    @GetMapping
    public BuyerRequestDtos.PageResponse list(
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public BuyerRequestDtos.Response getById(
            @PathVariable("id") UUID id
    ) {
        return service.getById(id);
    }
}