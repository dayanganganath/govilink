package com.govilink.quotation;

import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.harvest.HarvestStatus;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class QuotationIssuancePolicy {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Colombo");

    private QuotationIssuancePolicy() {
    }

    public static void validate(
            Quotation quotation,
            AppUser issuer,
            Instant now,
            Instant expiresAt,
            Set<UUID> confirmedLineIds
    ) {
        if (quotation == null
                || issuer == null
                || now == null
                || expiresAt == null
                || confirmedLineIds == null) {
            throw new IllegalArgumentException(
                    "Quotation, issuer, times and price confirmations are required."
            );
        }

        boolean staff = issuer.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (issuer.getId() == null || !issuer.isActive() || !staff) {
            throw new IllegalArgumentException(
                    "An active staff account is required."
            );
        }

        if (quotation.getId() == null) {
            throw new IllegalStateException(
                    "Save the draft before issuing it."
            );
        }

        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a draft quotation can be issued."
            );
        }

        var buyer = quotation.getBuyerRequest();

        if (buyer.getStatus() != BuyerRequestStatus.OPEN
                || now.atZone(BUSINESS_ZONE)
                        .toLocalDate()
                        .isAfter(quotation.getRequiredBy())) {
            throw new IllegalStateException(
                    "The buyer request is closed or its required date has passed."
            );
        }

        if (!expiresAt.isAfter(now)
                || expiresAt.atZone(BUSINESS_ZONE)
                        .toLocalDate()
                        .isAfter(quotation.getRequiredBy())) {
            throw new IllegalArgumentException(
                    "Expiry must be in the future and no later than the required date."
            );
        }

        if (!quotation.getBuyerName().equals(buyer.getBuyerName())
                || !quotation.getBuyerPhone().equals(buyer.getPhone())
                || !same(quotation.getDistrict(), buyer.getDistrict())
                || !quotation.getRequiredBy().equals(buyer.getRequiredBy())) {
            throw new IllegalStateException(
                    "Buyer details have changed. Prepare a new draft."
            );
        }

        var lines = quotation.getLines();

        if (lines.isEmpty()
                || quotation.getQuantityKg()
                        .compareTo(buyer.getQuantityKg()) != 0) {
            throw new IllegalStateException(
                    "Allocate the full requested quantity before issuing."
            );
        }

        Set<UUID> expectedIds = new HashSet<>();

        for (var line : lines) {
            if (line.getId() == null
                    || !expectedIds.add(line.getId())) {
                throw new IllegalStateException(
                        "Save valid quotation lines before issuing."
                );
            }

            var harvest = line.getHarvestListing();

            boolean invalidHarvest =
                    harvest.getStatus() != HarvestStatus.AVAILABLE
                    || !harvest.getFarmer().isActive()
                    || harvest.getQuantityKg()
                            .compareTo(line.getQuantityKg()) < 0
                    || harvest.getAvailableFrom()
                            .isAfter(quotation.getRequiredBy())
                    || !same(
                            harvest.getCropName(),
                            line.getCropName()
                    )
                    || !same(
                            line.getCropName(),
                            buyer.getCropName()
                    )
                    || !same(
                            harvest.getFarmer().getDistrict(),
                            quotation.getDistrict()
                    );

            if (invalidHarvest) {
                throw new IllegalStateException(
                        "A selected harvest has changed or is no longer eligible. Prepare a new draft."
                );
            }

            if (buyer.getMaxPricePerKg() != null
                    && line.getFarmerPricePerKg()
                            .compareTo(buyer.getMaxPricePerKg()) > 0) {
                throw new IllegalStateException(
                        "A quoted farmer price exceeds the buyer's current price limit."
                );
            }
        }

        if (!expectedIds.equals(confirmedLineIds)) {
            throw new IllegalArgumentException(
                    "Explicitly confirm the current price of every quotation line."
            );
        }
    }

    private static boolean same(
            String first,
            String second
    ) {
        return first.strip().equalsIgnoreCase(second.strip());
    }
}