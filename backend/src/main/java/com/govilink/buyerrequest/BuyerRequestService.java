package com.govilink.buyerrequest;

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
public class BuyerRequestService {

    private final BuyerRequestRepository repository;

    public BuyerRequestService(BuyerRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BuyerRequestDtos.Response create(
            BuyerRequestDtos.CreateRequest request
    ) {
        BuyerRequest buyerRequest = new BuyerRequest(
                request.buyerName().strip(),
                request.phone().strip(),
                request.cropName().strip(),
                request.quantityKg().setScale(
                        3,
                        RoundingMode.UNNECESSARY
                ),
                request.district().strip(),
                request.requiredBy(),
                request.maxPricePerKg() == null
                        ? null
                        : request.maxPricePerKg().setScale(
                                2,
                                RoundingMode.UNNECESSARY
                        )
        );

        return toResponse(repository.saveAndFlush(buyerRequest));
    }

    public BuyerRequestDtos.Response getById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer request not found."
                ));
    }

    public BuyerRequestDtos.PageResponse list(int page, int size) {
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

        Page<BuyerRequest> result = repository.findAll(pageable);

        return new BuyerRequestDtos.PageResponse(
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

    private BuyerRequestDtos.Response toResponse(
            BuyerRequest request
    ) {
        return new BuyerRequestDtos.Response(
                request.getId(),
                request.getBuyerName(),
                request.getPhone(),
                request.getCropName(),
                request.getQuantityKg(),
                request.getDistrict(),
                request.getRequiredBy(),
                request.getMaxPricePerKg(),
                request.getStatus(),
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}