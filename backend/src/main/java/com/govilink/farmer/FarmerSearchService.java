package com.govilink.farmer;

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

@Service
@Transactional(readOnly = true)
public class FarmerSearchService {

    private final FarmerRepository repository;

    public FarmerSearchService(FarmerRepository repository) {
        this.repository = repository;
    }

    public FarmerSearchResponse search(
            String query,
            String district,
            Boolean active,
            int page,
            int size
    ) {
        validatePagination(page, size);

        String normalizedQuery = normalize(query, 150, "q");
        String normalizedDistrict = normalize(district, 100, "district");

        Specification<Farmer> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (normalizedQuery != null) {
                String pattern = "%"
                        + escapeLike(normalizedQuery)
                        + "%";

                predicates.add(cb.or(
                        cb.like(
                                cb.lower(root.<String>get("fullName")),
                                pattern,
                                '!'
                        ),
                        cb.like(
                                cb.lower(root.<String>get("phone")),
                                pattern,
                                '!'
                        ),
                        cb.like(
                                cb.lower(root.<String>get("village")),
                                pattern,
                                '!'
                        )
                ));
            }

            if (normalizedDistrict != null) {
                predicates.add(cb.equal(
                        cb.lower(root.<String>get("district")),
                        normalizedDistrict
                ));
            }

            if (active != null) {
                predicates.add(cb.equal(
                        root.<Boolean>get("active"),
                        active
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<Farmer> result = repository.findAll(
                specification,
                pageable
        );

        List<FarmerDtos.Response> content = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new FarmerSearchResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100."
            );
        }
    }

    private String normalize(
            String value,
            int maximumLength,
            String parameter
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        if (normalized.length() > maximumLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parameter '" + parameter + "' must not exceed "
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

    private FarmerDtos.Response toResponse(Farmer farmer) {
        return new FarmerDtos.Response(
                farmer.getId(),
                farmer.getFullName(),
                farmer.getPhone(),
                farmer.getDistrict(),
                farmer.getVillage(),
                farmer.isActive(),
                farmer.getVersion(),
                farmer.getCreatedAt(),
                farmer.getUpdatedAt()
        );
    }
}