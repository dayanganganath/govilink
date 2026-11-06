package com.govilink.order;

import com.govilink.buyerrequest.BuyerRequest;
import com.govilink.buyerrequest.BuyerRequestStatus;
import com.govilink.identity.AppUser;
import com.govilink.identity.UserRole;
import com.govilink.quotation.Quotation;
import com.govilink.quotation.QuotationStatus;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "managed_orders")
public class ManagedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_request_id", nullable = false)
    private BuyerRequest buyerRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private AppUser recordedBy;

    @Column(name = "buyer_contact_name", nullable = false, length = 150)
    private String buyerContactName;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_channel", nullable = false, length = 20)
    private ApprovalChannel approvalChannel;

    @Column(name = "approval_reference", nullable = false, length = 500)
    private String approvalReference;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CONFIRMED;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "order",
            cascade = {
                    CascadeType.PERSIST,
                    CascadeType.MERGE
            }
    )
    private List<OrderReservation> reservations = new ArrayList<>();

    protected ManagedOrder() {
    }

    public ManagedOrder(
            Quotation quotation,
            AppUser recordedBy,
            ApprovalDetails approval,
            Instant now
    ) {
        Objects.requireNonNull(
                quotation,
                "Quotation is required."
        );

        Objects.requireNonNull(
                recordedBy,
                "Staff account is required."
        );

        Objects.requireNonNull(
                approval,
                "Buyer approval details are required."
        );

        Objects.requireNonNull(
                now,
                "Recording time is required."
        );

        approval.validateAt(now);

        boolean staffRole = recordedBy.getRoles().stream()
                .anyMatch(role ->
                        role == UserRole.ADMIN
                                || role == UserRole.COORDINATOR
                );

        if (recordedBy.getId() == null
                || !recordedBy.isActive()
                || !staffRole) {
            throw new IllegalArgumentException(
                    "An active saved staff account is required."
            );
        }

        if (quotation.getId() == null
                || quotation.getStatus() != QuotationStatus.ISSUED) {
            throw new IllegalStateException(
                    "Only a saved issued quotation can become an order."
            );
        }

        if (quotation.getExpiresAt() == null
                || !quotation.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException(
                    "This quotation has expired."
            );
        }

        if (quotation.getIssuedAt() == null
                || approval.approvedAt()
                        .isBefore(quotation.getIssuedAt())) {
            throw new IllegalArgumentException(
                    "Buyer approval cannot be earlier than quotation issuance."
            );
        }

        BuyerRequest buyer = quotation.getBuyerRequest();

        if (buyer.getStatus() != BuyerRequestStatus.OPEN
                || buyer.getRequiredBy().isBefore(
                        now.atZone(ZoneId.of("Asia/Colombo"))
                                .toLocalDate()
                )) {
            throw new IllegalStateException(
                    "The buyer request is closed or its required date has passed."
            );
        }

        if (quotation.getLines().isEmpty()
                || quotation.getQuantityKg()
                        .compareTo(buyer.getQuantityKg()) != 0) {
            throw new IllegalStateException(
                    "The quotation must cover the full requested quantity."
            );
        }

        this.quotation = quotation;
        this.buyerRequest = buyer;
        this.recordedBy = recordedBy;
        this.buyerContactName = approval.buyerContactName();
        this.approvalChannel = approval.channel();
        this.approvalReference = approval.reference();
        this.approvedAt = approval.approvedAt();
        this.recordedAt = now;

        Set<UUID> sources = new HashSet<>();

        for (var line : quotation.getLines()) {
            if (line.getId() == null
                    || !line.isPriceConfirmed()
                    || !sources.add(line.getHarvestListing().getId())) {
                throw new IllegalStateException(
                        "Saved quotation lines with confirmed prices and unique sources are required."
                );
            }

            reservations.add(
                    new OrderReservation(this, line, now)
            );
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public BuyerRequest getBuyerRequest() {
        return buyerRequest;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }

    public String getBuyerContactName() {
        return buyerContactName;
    }

    public ApprovalChannel getApprovalChannel() {
        return approvalChannel;
    }

    public String getApprovalReference() {
        return approvalReference;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderReservation> getReservations() {
        return List.copyOf(reservations);
    }
}