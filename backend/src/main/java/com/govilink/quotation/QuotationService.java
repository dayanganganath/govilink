package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequestRepository;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.harvest.HarvestRepository;
import com.govilink.identity.AppUser;
import com.govilink.identity.AppUserRepository;
import com.govilink.identity.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
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
        AppUser actor = requireStaff(staffEmail);

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

    @Transactional
    public QuotationDtos.Response issue(
            UUID id,
            QuotationDtos.IssueRequest request,
            String staffEmail
    ) {
        AppUser actor = requireStaff(staffEmail);

        Quotation quotation = quotations.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Quotation not found."
                        )
                );

        if (quotation.getVersion() != request.version()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quotation has changed. Reload and review it again."
            );
        }

        var confirmed = new HashSet<>(
                request.confirmedLineIds()
        );

        if (confirmed.size() != request.confirmedLineIds().size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Each quotation line may be confirmed only once."
            );
        }

        try {
            quotation.issue(
                    actor,
                    Instant.now().truncatedTo(ChronoUnit.MICROS),
                    request.expiresAt().truncatedTo(ChronoUnit.MICROS),
                    confirmed
            );

            return response(
                    quotations.saveAndFlush(quotation)
            );
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
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quotation has changed. Reload and review it again.",
                    exception
            );
        }
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

    private AppUser requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        AppUser actor = users.findByEmailIgnoreCase(email)
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

        return actor;
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

        UUID issuedBy = quotation.getIssuedBy() == null
                ? null
                : quotation.getIssuedBy().getId();

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
                issuedBy,
                quotation.getIssuedAt(),
                quotation.getExpiresAt(),
                lines
        );
    }
}