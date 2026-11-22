CREATE TABLE order_dispatches (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE
        REFERENCES managed_orders(id),
    dispatched_by UUID NOT NULL
        REFERENCES app_users(id),
    dispatched_at TIMESTAMPTZ NOT NULL,
    reference VARCHAR(500) NOT NULL,
    order_version_at_dispatch BIGINT NOT NULL,

    CONSTRAINT ck_order_dispatch_reference
        CHECK (length(trim(reference)) > 0),

    CONSTRAINT ck_order_dispatch_version
        CHECK (order_version_at_dispatch >= 0)
);

CREATE INDEX ix_order_dispatches_dispatched_by
    ON order_dispatches(dispatched_by);