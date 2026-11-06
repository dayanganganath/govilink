package com.govilink.order;

import java.time.Instant;

public record ApprovalDetails(
        String buyerContactName,
        ApprovalChannel channel,
        String reference,
        Instant approvedAt
) {

    public ApprovalDetails {
        buyerContactName = requiredText(
                buyerContactName,
                "Buyer contact name",
                150
        );

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Approval channel is required."
            );
        }

        reference = requiredText(
                reference,
                "Approval reference",
                500
        );

        if (approvedAt == null) {
            throw new IllegalArgumentException(
                    "Buyer approval time is required."
            );
        }
    }

    public void validateAt(Instant recordedAt) {
        if (recordedAt == null) {
            throw new IllegalArgumentException(
                    "Recording time is required."
            );
        }

        if (approvedAt.isAfter(recordedAt)) {
            throw new IllegalArgumentException(
                    "Buyer approval time cannot be in the future."
            );
        }
    }

    private static String requiredText(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        String cleaned = value.strip();

        if (cleaned.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed "
                            + maximumLength + " characters."
            );
        }

        return cleaned;
    }
}