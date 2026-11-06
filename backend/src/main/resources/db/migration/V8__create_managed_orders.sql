CREATE TABLE managed_orders (
    id UUID PRIMARY KEY,

    quotation_id UUID NOT NULL REFERENCES quotations(id),
    buyer_request_id UUID NOT NULL REFERENCES buyer_requests(id),
    recorded_by UUID NOT NULL REFERENCES app_users(id),

    buyer_contact_name VARCHAR(150) NOT NULL,
    approval_channel VARCHAR(20) NOT NULL,
    approval_reference VARCHAR(500) NOT NULL,

    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',

    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_managed_orders_quotation
        UNIQUE (quotation_id),

    CONSTRAINT ck_managed_orders_contact
        CHECK (length(trim(buyer_contact_name)) > 0),

    CONSTRAINT ck_managed_orders_reference
        CHECK (length(trim(approval_reference)) > 0),

    CONSTRAINT ck_managed_orders_channel
        CHECK (
            approval_channel IN (
                'PHONE',
                'WHATSAPP',
                'EMAIL',
                'IN_PERSON'
            )
        ),

    CONSTRAINT ck_managed_orders_approval_time
        CHECK (approved_at <= recorded_at),

    CONSTRAINT ck_managed_orders_status
        CHECK (
            status IN (
                'CONFIRMED',
                'COLLECTION_IN_PROGRESS',
                'COLLECTED',
                'OUT_FOR_DELIVERY',
                'DELIVERED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_managed_orders_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_managed_orders_active_buyer_request
    ON managed_orders (buyer_request_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_managed_orders_status_created
    ON managed_orders (status, created_at DESC, id DESC);

CREATE INDEX idx_managed_orders_recorded_by
    ON managed_orders (recorded_by);

CREATE TABLE order_reservations (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL REFERENCES managed_orders(id),
    quotation_line_id UUID NOT NULL REFERENCES quotation_lines(id),
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id),

    quantity_kg NUMERIC(12, 3) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_order_reservations_line
        UNIQUE (quotation_line_id),

    CONSTRAINT uq_order_reservations_source
        UNIQUE (order_id, harvest_listing_id),

    CONSTRAINT ck_order_reservations_quantity
        CHECK (
            quantity_kg > 0
            AND quantity_kg <= 999999999.999
        ),

    CONSTRAINT ck_order_reservations_status
        CHECK (
            status IN (
                'ACTIVE',
                'RELEASED',
                'CONSUMED'
            )
        ),

    CONSTRAINT ck_order_reservations_finished
        CHECK (
            (status = 'ACTIVE' AND finished_at IS NULL)
            OR
            (
                status IN ('RELEASED', 'CONSUMED')
                AND finished_at IS NOT NULL
                AND finished_at >= reserved_at
            )
        ),

    CONSTRAINT ck_order_reservations_version
        CHECK (version >= 0)
);

CREATE INDEX idx_order_reservations_harvest_status
    ON order_reservations (harvest_listing_id, status);