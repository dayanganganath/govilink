package com.govilink.harvest;

import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HarvestService {

    private static final String VERSION_CONFLICT =
            "Harvest listing was changed by another request. "
                    + "Reload the latest details and try again.";

    private final HarvestRepository repository;
    private final FarmerRepository farmerRepository;

    public HarvestService(
            HarvestRepository repository,
            FarmerRepository farmerRepository
    ) {
        this.repository = repository;
        this.farmerRepository = farmerRepository;
    }

    @Transactional
    public HarvestDtos.Response create(
            HarvestDtos.CreateRequest request
    ) {
        Farmer farmer = farmerRepository.findById(request.farmerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Farmer not found."
                ));

        if (!farmer.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot create a harvest listing for an inactive farmer."
            );
        }

        HarvestListing listing = new HarvestListing(
                farmer,
                request.cropName().strip(),
                request.quantityKg().setScale(
                        3,
                        RoundingMode.UNNECESSARY
                ),
                request.expectedPricePerKg() == null
                        ? null
                        : request.expectedPricePerKg().setScale(
                                2,
                                RoundingMode.UNNECESSARY
                        ),
                request.availableFrom()
        );

        return toResponse(repository.saveAndFlush(listing));
    }

    public HarvestDtos.Response getById(UUID id) {
        return toResponse(findListing(id));
    }

    @Transactional
    public HarvestDtos.Response updateStatus(
            UUID id,
            HarvestStatusRequest request
    ) {
        if (request.status() != HarvestStatus.SOLD
                && request.status() != HarvestStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be SOLD or CANCELLED."
            );
        }

        HarvestListing listing = findListing(id);

        if (listing.getVersion() != request.version().longValue()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    VERSION_CONFLICT
            );
        }

        if (listing.getStatus() != HarvestStatus.AVAILABLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an available harvest listing can be closed."
            );
        }

        if (request.status() == HarvestStatus.SOLD) {
            listing.markSold();
        } else {
            listing.cancel();
        }

        try {
            return toResponse(repository.saveAndFlush(listing));
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    VERSION_CONFLICT,
                    exception
            );
        }
    }

    public HarvestDtos.PageResponse list(
            UUID farmerId,
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

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<HarvestListing> result = farmerId == null
                ? repository.findAll(pageable)
                : repository.findByFarmer_Id(farmerId, pageable);

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

    private HarvestListing findListing(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Harvest listing not found."
                ));
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