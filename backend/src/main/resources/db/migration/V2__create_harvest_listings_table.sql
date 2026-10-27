CREATE TABLE harvest_listings (
    id UUID PRIMARY KEY,
    farmer_id UUID NOT NULL,
    crop_name VARCHAR(100) NOT NULL,
    quantity_kg NUMERIC(12, 3) NOT NULL,
    expected_price_per_kg NUMERIC(12, 2),
    available_from DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_harvest_listings_farmer
        FOREIGN KEY (farmer_id)
        REFERENCES farmers(id),

    CONSTRAINT ck_harvest_listings_crop_name
        CHECK (length(trim(crop_name)) > 0),

    CONSTRAINT ck_harvest_listings_quantity
        CHECK (quantity_kg > 0),

    CONSTRAINT ck_harvest_listings_price
        CHECK (
            expected_price_per_kg IS NULL
            OR expected_price_per_kg > 0
        ),

    CONSTRAINT ck_harvest_listings_status
        CHECK (status IN ('AVAILABLE', 'SOLD', 'CANCELLED'))
);

CREATE INDEX idx_harvest_listings_created
    ON harvest_listings (created_at DESC, id DESC);

CREATE INDEX idx_harvest_listings_farmer_created
    ON harvest_listings (farmer_id, created_at DESC, id DESC);