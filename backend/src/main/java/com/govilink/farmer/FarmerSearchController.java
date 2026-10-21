package com.govilink.farmer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farmers/search")
public class FarmerSearchController {

    private final FarmerSearchService service;

    public FarmerSearchController(FarmerSearchService service) {
        this.service = service;
    }

    @GetMapping
    public FarmerSearchResponse search(
            @RequestParam(name = "q", required = false) String query,

            @RequestParam(name = "district", required = false)
            String district,

            @RequestParam(name = "active", required = false)
            Boolean active,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        return service.search(
                query,
                district,
                active,
                page,
                size
        );
    }
}