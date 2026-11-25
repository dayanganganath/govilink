CREATE TABLE order_cancellations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES managed_orders(id),
    cancelled_by UUID NOT NULL REFERENCES app_users(id),
    cancelled_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    order_version_at_cancellation BIGINT NOT NULL,
    released_quantity_kg NUMERIC(12, 3) NOT NULL,

    CONSTRAINT ck_order_cancellation_reason
        CHECK (length(trim(reason)) > 0),

    CONSTRAINT ck_order_cancellation_previous_status
        CHECK (previous_status IN ('CONFIRMED', 'COLLECTION_IN_PROGRESS')),

    CONSTRAINT ck_order_cancellation_version
        CHECK (order_version_at_cancellation >= 0),

    CONSTRAINT ck_order_cancellation_quantity
        CHECK (released_quantity_kg > 0)
);

CREATE INDEX ix_order_cancellations_cancelled_by
    ON order_cancellations(cancelled_by);
