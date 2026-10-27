package com.govilink.harvest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HarvestStatusRequest(
        @NotNull
        HarvestStatus status,

        @NotNull
        @Min(0)
        Long version
) {
}