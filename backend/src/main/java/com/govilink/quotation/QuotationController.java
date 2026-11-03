package com.govilink.quotation;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<QuotationDtos.Response> createDraft(
            @Valid @RequestBody QuotationDtos.CreateDraft request,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        var response = service.createDraft(
                request,
                authentication.getName()
        );

        URI location = URI.create(
                "/api/v1/quotations/" + response.id()
        );

        return ResponseEntity.created(location)
                .body(response);
    }

    @PostMapping("/{id}/issue")
    public QuotationDtos.Response issue(
            @PathVariable("id") UUID id,
            @Valid @RequestBody QuotationDtos.IssueRequest request,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return service.issue(
                id,
                request,
                authentication.getName()
        );
    }

    @GetMapping("/{id}")
    public QuotationDtos.Response getById(
            @PathVariable("id") UUID id
    ) {
        return service.getById(id);
    }
}