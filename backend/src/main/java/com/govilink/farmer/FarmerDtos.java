package com.govilink.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class FarmerDtos {

    private FarmerDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 150)
            String fullName,

            @NotBlank
            @Size(max = 20)
            String phone,

            @NotBlank
            @Size(max = 100)
            String district,

            @Size(max = 150)
            String village
    ) {
    }

    public record Response(
            UUID id,
            String fullName,
            String phone,
            String district,
            String village,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}