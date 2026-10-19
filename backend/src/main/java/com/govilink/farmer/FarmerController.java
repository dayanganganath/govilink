package com.govilink.farmer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/farmers")
public class FarmerController {

    private final FarmerService service;

    public FarmerController(FarmerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmerDtos.Response create(
            @Valid @RequestBody FarmerDtos.CreateRequest request
    ) {
        return service.create(request);
    }

    @GetMapping
    public List<FarmerDtos.Response> listLatest() {
        return service.listLatest();
    }
}
