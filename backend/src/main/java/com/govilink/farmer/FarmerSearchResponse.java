package com.govilink.farmer;

import java.util.List;

public record FarmerSearchResponse(
        List<FarmerDtos.Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}