package com.govilink.harvest;

import com.govilink.farmer.Farmer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HarvestSearchService {

    private final HarvestRepository repository;

    public HarvestSearchService(HarvestRepository repository) {
        this.repository = repository;
    }

    public HarvestDtos.PageResponse search(
            String query,
            String district,
            HarvestStatus status,
            UUID farmerId,
            int page,
            int size
    ) {
        if (page < 0) {
            throw badRequest("Page must be zero or greater.");
        }

        if (size < 1 || size > 100) {
            throw badRequest("Size must be between 1 and 100.");
        }

        String normalizedQuery = normalize(query, "Query", 100);
        String normalizedDistrict = normalize(district, "District", 100);

        Specification<HarvestListing> specification =
                (root, criteriaQuery, builder) -> {
                    List<Predicate> predicates = new ArrayList<>();

                    if (normalizedQuery != null) {
                        predicates.add(builder.like(
                                builder.lower(root.get("cropName")),
                                "%" + escapeLike(normalizedQuery) + "%",
                                '!'
                        ));
                    }

                    if (normalizedDistrict != null) {
                        predicates.add(builder.equal(
                                builder.lower(
                                        root.get("farmer").get("district")
                                ),
                                normalizedDistrict
                        ));
                    }

                    if (status != null) {
                        predicates.add(builder.equal(
                                root.get("status"),
                                status
                        ));
                    }

                    if (farmerId != null) {
                        predicates.add(builder.equal(
                                root.get("farmer").get("id"),
                                farmerId
                        ));
                    }

                    return builder.and(
                            predicates.toArray(Predicate[]::new)
                    );
                };

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<HarvestListing> result = repository.findAll(
                specification,
                pageable
        );

        return new HarvestDtos.PageResponse(
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private String normalize(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        if (normalized.length() > maximumLength) {
            throw badRequest(
                    field + " must not exceed "
                            + maximumLength + " characters."
            );
        }

        return normalized.isEmpty()
                ? null
                : normalized.toLowerCase(Locale.ROOT);
    }

    private String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                reason
        );
    }

    private HarvestDtos.Response toResponse(HarvestListing listing) {
        Farmer farmer = listing.getFarmer();

        return new HarvestDtos.Response(
                listing.getId(),
                farmer.getId(),
                farmer.getFullName(),
                farmer.getDistrict(),
                listing.getCropName(),
                listing.getQuantityKg(),
                listing.getExpectedPricePerKg(),
                listing.getAvailableFrom(),
                listing.getStatus(),
                listing.getVersion(),
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }
}