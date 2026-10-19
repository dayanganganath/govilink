package com.govilink.farmer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FarmerUpdateRequest(

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
        String village,

        @NotNull
        @Min(0)
        Long version

) {
}