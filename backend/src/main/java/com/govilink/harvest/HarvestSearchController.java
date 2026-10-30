package com.govilink.harvest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/harvest-listings/search")
public class HarvestSearchController {

    private final HarvestSearchService service;

    public HarvestSearchController(HarvestSearchService service) {
        this.service = service;
    }

    @GetMapping
    public HarvestDtos.PageResponse search(
            @RequestParam(name = "q", required = false)
            String query,

            @RequestParam(name = "district", required = false)
            String district,

            @RequestParam(name = "status", required = false)
            HarvestStatus status,

            @RequestParam(name = "farmerId", required = false)
            UUID farmerId,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        return service.search(
                query,
                district,
                status,
                farmerId,
                page,
                size
        );
    }
}