package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.harvest.HarvestRepository;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuotationService {

    private final QuotationRepository quotations;
    private final BuyerRequestRepository buyers;
    private final HarvestRepository harvests;
    private final AppUserRepository users;

    public QuotationService(
            QuotationRepository quotations,
            BuyerRequestRepository buyers,
            HarvestRepository harvests,
            AppUserRepository users
    ) {
        this.quotations = quotations;
        this.buyers = buyers;
        this.harvests = harvests;
        this.users = users;
    }

    @Transactional
    public QuotationDtos.Response createDraft(
            QuotationDtos.CreateDraft request,
            String staffEmail
    ) {
        var actor = users.findByEmailIgnoreCase(staffEmail)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED
                        )
                );

        boolean staffRole = actor.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (!actor.isActive() || !staffRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active staff access is required."
            );
        }

        var buyer = buyers.findById(request.buyerRequestId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Buyer request not found."
                        )
                );

        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Colombo")
        );

        if (buyer.getStatus() != BuyerRequestStatus.OPEN
                || buyer.getRequiredBy().isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The buyer request is closed or its required date has passed."
            );
        }

        Quotation quotation;

        try {
            quotation = new Quotation(
                    buyer,
                    actor,
                    request.serviceFee(),
                    request.deliveryFee(),
                    request.deliveryAddress()
            );

            for (var line : request.lines()) {
                var harvest = harvests.findById(
                        line.harvestListingId()
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Harvest listing not found."
                        )
                );

                quotation.addLine(
                        harvest,
                        line.quantityKg(),
                        line.farmerPricePerKg(),
                        line.priceConfirmed()
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }

        return response(
                quotations.saveAndFlush(quotation)
        );
    }

    @Transactional(readOnly = true)
    public QuotationDtos.Response getById(UUID id) {
        Quotation quotation = quotations.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Quotation not found."
                        )
                );

        return response(quotation);
    }

    private QuotationDtos.Response response(
            Quotation quotation
    ) {
        var lines = quotation.getLines().stream()
                .map(line -> {
                    var harvest = line.getHarvestListing();
                    var farmer = harvest.getFarmer();

                    return new QuotationDtos.LineResponse(
                            line.getId(),
                            line.getLineNumber(),
                            harvest.getId(),
                            farmer.getId(),
                            farmer.getFullName(),
                            line.getCropName(),
                            line.getQuantityKg(),
                            line.getFarmerPricePerKg(),
                            line.getLineSubtotal(),
                            line.isPriceConfirmed()
                    );
                })
                .toList();

        boolean confirmationNeeded = lines.stream()
                .anyMatch(line -> !line.priceConfirmed());

        return new QuotationDtos.Response(
                quotation.getId(),
                quotation.getBuyerRequest().getId(),
                quotation.getCreatedBy().getId(),
                quotation.getBuyerName(),
                quotation.getBuyerPhone(),
                quotation.getDistrict(),
                quotation.getRequiredBy(),
                quotation.getDeliveryAddress(),
                quotation.getStatus(),
                quotation.getCurrency(),
                quotation.getQuantityKg(),
                quotation.getProduceSubtotal(),
                quotation.getServiceFee(),
                quotation.getDeliveryFee(),
                quotation.getTotal(),
                confirmationNeeded,
                quotation.getVersion(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt(),
                lines
        );
    }
}