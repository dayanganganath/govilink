package com.govilink.matching;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buyer-requests/{id}/matches")
public class BuyerMatchController {

    private final BuyerMatchService service;

    public BuyerMatchController(BuyerMatchService service) {
        this.service = service;
    }

    @GetMapping
    public BuyerMatchDtos.PageResponse matches(
            @PathVariable("id")
            UUID id,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        return service.matches(id, page, size);
    }
}