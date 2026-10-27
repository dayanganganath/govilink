package com.govilink.harvest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/harvest-listings")
public class HarvestController {

    private final HarvestService service;

    public HarvestController(HarvestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HarvestDtos.Response create(
            @Valid @RequestBody HarvestDtos.CreateRequest request
    ) {
        return service.create(request);
    }

    @GetMapping
    public HarvestDtos.PageResponse list(
            @RequestParam(name = "farmerId", required = false)
            UUID farmerId,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        return service.list(farmerId, page, size);
    }

    @GetMapping("/{id}")
    public HarvestDtos.Response getById(
            @PathVariable("id") UUID id
    ) {
        return service.getById(id);
    }

    @PatchMapping("/{id}/status")
    public HarvestDtos.Response updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody HarvestStatusRequest request
    ) {
        return service.updateStatus(id, request);
    }
}