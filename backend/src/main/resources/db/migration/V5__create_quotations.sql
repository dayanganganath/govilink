CREATE TABLE quotations (
    id UUID PRIMARY KEY,
    buyer_request_id UUID NOT NULL REFERENCES buyer_requests(id),
    created_by UUID NOT NULL REFERENCES app_users(id),

    buyer_name VARCHAR(150) NOT NULL,
    buyer_phone VARCHAR(20) NOT NULL,
    district VARCHAR(100) NOT NULL,
    required_by DATE NOT NULL,
    delivery_address VARCHAR(500),

    currency VARCHAR(3) NOT NULL DEFAULT 'LKR',
    service_fee NUMERIC(12, 2) NOT NULL,
    delivery_fee NUMERIC(12, 2) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_quotations_buyer_name
        CHECK (length(trim(buyer_name)) > 0),

    CONSTRAINT ck_quotations_buyer_phone
        CHECK (length(trim(buyer_phone)) > 0),

    CONSTRAINT ck_quotations_district
        CHECK (length(trim(district)) > 0),

    CONSTRAINT ck_quotations_address
        CHECK (
            delivery_address IS NULL
            OR length(trim(delivery_address)) > 0
        ),

    CONSTRAINT ck_quotations_currency
        CHECK (currency = 'LKR'),

    CONSTRAINT ck_quotations_service_fee
        CHECK (
            service_fee >= 0
            AND service_fee <= 9999999999.99
        ),

    CONSTRAINT ck_quotations_delivery_fee
        CHECK (
            delivery_fee >= 0
            AND delivery_fee <= 9999999999.99
        ),

    CONSTRAINT ck_quotations_status
        CHECK (
            status IN (
                'DRAFT',
                'ISSUED',
                'ACCEPTED',
                'REJECTED',
                'EXPIRED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_quotations_expiry
        CHECK (
            status IN ('DRAFT', 'CANCELLED')
            OR expires_at IS NOT NULL
        ),

    CONSTRAINT ck_quotations_version
        CHECK (version >= 0)
);

CREATE TABLE quotation_lines (
    id UUID PRIMARY KEY,
    quotation_id UUID NOT NULL REFERENCES quotations(id),
    line_number INTEGER NOT NULL,
    harvest_listing_id UUID NOT NULL REFERENCES harvest_listings(id),

    crop_name VARCHAR(100) NOT NULL,
    quantity_kg NUMERIC(12, 3) NOT NULL,
    farmer_price_per_kg NUMERIC(12, 2) NOT NULL,
    price_confirmed BOOLEAN NOT NULL DEFAULT FALSE,

    line_subtotal NUMERIC(24, 2) GENERATED ALWAYS AS (
        round(quantity_kg * farmer_price_per_kg, 2)
    ) STORED,

    CONSTRAINT uq_quotation_lines_number
        UNIQUE (quotation_id, line_number),

    CONSTRAINT uq_quotation_lines_harvest
        UNIQUE (quotation_id, harvest_listing_id),

    CONSTRAINT ck_quotation_lines_number
        CHECK (line_number BETWEEN 1 AND 50),

    CONSTRAINT ck_quotation_lines_crop
        CHECK (length(trim(crop_name)) > 0),

    CONSTRAINT ck_quotation_lines_quantity
        CHECK (
            quantity_kg > 0
            AND quantity_kg <= 999999999.999
        ),

    CONSTRAINT ck_quotation_lines_price
        CHECK (
            farmer_price_per_kg > 0
            AND farmer_price_per_kg <= 9999999999.99
        )
);

CREATE INDEX idx_quotations_buyer_created
    ON quotations (
        buyer_request_id,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_quotations_status_created
    ON quotations (
        status,
        created_at DESC,
        id DESC
    );

CREATE INDEX idx_quotations_created_by
    ON quotations (created_by);

CREATE INDEX idx_quotation_lines_harvest
    ON quotation_lines (harvest_listing_id);