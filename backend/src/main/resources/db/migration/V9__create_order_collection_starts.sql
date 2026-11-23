CREATE TABLE order_collection_starts (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL REFERENCES managed_orders(id),
    started_by UUID NOT NULL REFERENCES app_users(id),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,

    reference VARCHAR(500) NOT NULL,
    order_version_at_start BIGINT NOT NULL,

    CONSTRAINT uq_order_collection_starts_order
        UNIQUE (order_id),

    CONSTRAINT ck_order_collection_starts_reference
        CHECK (length(trim(reference)) > 0),

    CONSTRAINT ck_order_collection_starts_version
        CHECK (order_version_at_start >= 0)
);

CREATE INDEX idx_order_collection_starts_staff
    ON order_collection_starts (started_by);