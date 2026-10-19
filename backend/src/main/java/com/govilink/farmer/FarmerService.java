package com.govilink.farmer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FarmerService {

    private final FarmerRepository repository;

    public FarmerService(FarmerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FarmerDtos.Response create(FarmerDtos.CreateRequest request) {
        Farmer farmer = new Farmer(
                request.fullName().strip(),
                request.phone().strip(),
                request.district().strip(),
                normalizeVillage(request.village())
        );

        return toResponse(repository.saveAndFlush(farmer));
    }

    public List<FarmerDtos.Response> listLatest() {
        PageRequest page = PageRequest.of(
                0,
                50,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return repository.findAll(page)
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FarmerDtos.Response getById(UUID id) {
        return toResponse(findFarmer(id));
    }

    @Transactional
    public FarmerDtos.Response update(
            UUID id,
            FarmerUpdateRequest request
    ) {
        Farmer farmer = findFarmer(id);

        if (farmer.getVersion() != request.version().longValue()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Farmer was changed by another request. "
                            + "Reload the latest details and try again."
            );
        }

        farmer.updateDetails(
                request.fullName().strip(),
                request.phone().strip(),
                request.district().strip(),
                normalizeVillage(request.village())
        );

        return toResponse(repository.saveAndFlush(farmer));
    }

    private Farmer findFarmer(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Farmer not found"
                ));
    }

    private String normalizeVillage(String village) {
        if (village == null) {
            return null;
        }

        String normalized = village.strip();

        return normalized.isEmpty() ? null : normalized;
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