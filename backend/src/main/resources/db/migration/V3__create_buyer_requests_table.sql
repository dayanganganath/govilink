CREATE TABLE buyer_requests (
    id UUID PRIMARY KEY,
    buyer_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    crop_name VARCHAR(100) NOT NULL,
    quantity_kg NUMERIC(12, 3) NOT NULL,
    district VARCHAR(100) NOT NULL,
    required_by DATE NOT NULL,
    max_price_per_kg NUMERIC(12, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_buyer_requests_buyer_name
        CHECK (length(trim(buyer_name)) > 0),

    CONSTRAINT ck_buyer_requests_phone
        CHECK (length(trim(phone)) > 0),

    CONSTRAINT ck_buyer_requests_crop_name
        CHECK (length(trim(crop_name)) > 0),

    CONSTRAINT ck_buyer_requests_district
        CHECK (length(trim(district)) > 0),

    CONSTRAINT ck_buyer_requests_quantity
        CHECK (quantity_kg > 0),

    CONSTRAINT ck_buyer_requests_price
        CHECK (
            max_price_per_kg IS NULL
            OR max_price_per_kg > 0
        ),

    CONSTRAINT ck_buyer_requests_status
        CHECK (status IN ('OPEN', 'FULFILLED', 'CANCELLED'))
);

CREATE INDEX idx_buyer_requests_created
    ON buyer_requests (created_at DESC, id DESC);