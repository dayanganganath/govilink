package com.govilink.order;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalDetailsTest {

    private static final Instant RECORDED_AT =
            Instant.parse("2030-01-02T10:00:00Z");

    private static final Instant APPROVED_AT =
            Instant.parse("2030-01-02T09:00:00Z");

    @ParameterizedTest
    @EnumSource(ApprovalChannel.class)
    void acceptsSupportedApprovalChannels(
            ApprovalChannel channel
    ) {
        ApprovalDetails details = new ApprovalDetails(
                "Test Buyer",
                channel,
                "Buyer confirmed the quotation.",
                APPROVED_AT
        );

        assertDoesNotThrow(
                () -> details.validateAt(RECORDED_AT)
        );

        assertEquals(channel, details.channel());
    }

    @Test
    void trimsContactNameAndReference() {
        ApprovalDetails details = new ApprovalDetails(
                "  Test Buyer  ",
                ApprovalChannel.PHONE,
                "  Call approval recorded.  ",
                APPROVED_AT
        );

        assertEquals(
                "Test Buyer",
                details.buyerContactName()
        );

        assertEquals(
                "Call approval recorded.",
                details.reference()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingBuyerContactName(String name) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        name,
                        ApprovalChannel.PHONE,
                        "Call approval recorded.",
                        APPROVED_AT
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingApprovalReference(String reference) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        "Test Buyer",
                        ApprovalChannel.PHONE,
                        reference,
                        APPROVED_AT
                )
        );
    }

    @Test
    void rejectsOversizedBuyerContactName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        "A".repeat(151),
                        ApprovalChannel.EMAIL,
                        "Email approval recorded.",
                        APPROVED_AT
                )
        );
    }

    @Test
    void rejectsOversizedApprovalReference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        "Test Buyer",
                        ApprovalChannel.EMAIL,
                        "A".repeat(501),
                        APPROVED_AT
                )
        );
    }

    @Test
    void rejectsMissingChannel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        "Test Buyer",
                        null,
                        "Approval recorded.",
                        APPROVED_AT
                )
        );
    }

    @Test
    void rejectsMissingApprovalTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApprovalDetails(
                        "Test Buyer",
                        ApprovalChannel.PHONE,
                        "Call approval recorded.",
                        null
                )
        );
    }

    @Test
    void rejectsFutureApprovalTime() {
        ApprovalDetails details = new ApprovalDetails(
                "Test Buyer",
                ApprovalChannel.WHATSAPP,
                "Buyer confirmed by message.",
                RECORDED_AT.plusSeconds(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> details.validateAt(RECORDED_AT)
        );
    }

    @Test
    void acceptsApprovalAtTheRecordingTime() {
        ApprovalDetails details = new ApprovalDetails(
                "Test Buyer",
                ApprovalChannel.IN_PERSON,
                "Buyer confirmed in person.",
                RECORDED_AT
        );

        assertDoesNotThrow(
                () -> details.validateAt(RECORDED_AT)
        );
    }

    @Test
    void rejectsMissingRecordingTime() {
        ApprovalDetails details = new ApprovalDetails(
                "Test Buyer",
                ApprovalChannel.PHONE,
                "Call approval recorded.",
                APPROVED_AT
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> details.validateAt(null)
        );
    }
}