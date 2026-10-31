package com.govilink.matching;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.harvest.HarvestListing;
import com.govilink.harvest.HarvestRepository;
import com.govilink.harvest.HarvestStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BuyerMatchService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Colombo");

    private final BuyerRequestRepository buyerRepository;
    private final HarvestRepository harvestRepository;

    public BuyerMatchService(
            BuyerRequestRepository buyerRepository,
            HarvestRepository harvestRepository
    ) {
        this.buyerRepository = buyerRepository;
        this.harvestRepository = harvestRepository;
    }

    public BuyerMatchDtos.PageResponse matches(
            UUID buyerId,
            int page,
            int size
    ) {
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

        BuyerRequest buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer request not found."
                ));

        if (buyer.getStatus() != BuyerRequestStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Matches are available only for open buyer requests."
            );
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        if (buyer.getRequiredBy().isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This buyer request has passed its required date."
            );
        }

        String crop = buyer.getCropName()
                .strip()
                .toLowerCase(Locale.ROOT);

        String district = buyer.getDistrict()
                .strip()
                .toLowerCase(Locale.ROOT);

        Specification<HarvestListing> specification =
                (root, query, builder) -> {
                    var farmer = root.join("farmer");

                    List<Predicate> rules = new ArrayList<>();

                    rules.add(builder.equal(
                            root.get("status"),
                            HarvestStatus.AVAILABLE
                    ));

                    rules.add(builder.isTrue(
                            farmer.get("active")
                    ));

                    rules.add(builder.equal(
                            builder.lower(root.get("cropName")),
                            crop
                    ));

                    rules.add(builder.equal(
                            builder.lower(farmer.get("district")),
                            district
                    ));

                    rules.add(builder.greaterThanOrEqualTo(
                            root.get("quantityKg"),
                            buyer.getQuantityKg()
                    ));

                    rules.add(builder.lessThanOrEqualTo(
                            root.get("availableFrom"),
                            buyer.getRequiredBy()
                    ));

                    if (buyer.getMaxPricePerKg() != null) {
                        rules.add(builder.or(
                                builder.isNull(
                                        root.get("expectedPricePerKg")
                                ),
                                builder.lessThanOrEqualTo(
                                        root.get("expectedPricePerKg"),
                                        buyer.getMaxPricePerKg()
                                )
                        ));
                    }

                    return builder.and(
                            rules.toArray(Predicate[]::new)
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

        var results = harvestRepository.findAll(
                specification,
                pageable
        );

        var content = results.getContent()
                .stream()
                .map(listing -> {
                    var farmer = listing.getFarmer();

                    return new BuyerMatchDtos.Match(
                            listing.getId(),
                            farmer.getId(),
                            farmer.getFullName(),
                            farmer.getPhone(),
                            farmer.getDistrict(),
                            listing.getCropName(),
                            listing.getQuantityKg(),
                            listing.getExpectedPricePerKg(),
                            listing.getAvailableFrom(),
                            listing.getExpectedPricePerKg() == null
                    );
                })
                .toList();

        return new BuyerMatchDtos.PageResponse(
                content,
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.hasNext(),
                results.hasPrevious()
        );
    }
}