CREATE TABLE order_deliveries (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL UNIQUE
        REFERENCES managed_orders(id),

    recorded_by UUID NOT NULL
        REFERENCES app_users(id),

    receiver_name VARCHAR(150) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    reference VARCHAR(500) NOT NULL,
    order_version_at_delivery BIGINT NOT NULL,

    CONSTRAINT ck_order_delivery_receiver
        CHECK (length(trim(receiver_name)) > 0),

    CONSTRAINT ck_order_delivery_reference
        CHECK (length(trim(reference)) > 0),

    CONSTRAINT ck_order_delivery_time
        CHECK (delivered_at <= recorded_at),

    CONSTRAINT ck_order_delivery_version
        CHECK (order_version_at_delivery >= 0)
);

CREATE INDEX ix_order_deliveries_recorded_by
    ON order_deliveries(recorded_by);