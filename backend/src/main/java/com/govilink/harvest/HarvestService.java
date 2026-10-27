package com.govilink.harvest;

import com.govilink.farmer.Farmer;
import com.govilink.farmer.FarmerRepository;
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
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Harvest listing not found."
                ));
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