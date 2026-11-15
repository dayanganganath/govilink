ALTER TABLE order_reservations
    ADD COLUMN collected_quantity_kg NUMERIC(12, 3)
        NOT NULL DEFAULT 0;

ALTER TABLE order_reservations
    ADD CONSTRAINT ck_order_reservations_collected
        CHECK (
            collected_quantity_kg >= 0
            AND collected_quantity_kg <= quantity_kg
        ),
    ADD CONSTRAINT uq_order_reservations_order_id
        UNIQUE (order_id, id);

CREATE TABLE goods_receipts (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL REFERENCES managed_orders(id),
    reservation_id UUID NOT NULL,
    received_by UUID NOT NULL REFERENCES app_users(id),

    quantity_kg NUMERIC(12, 3) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,

    reference VARCHAR(500) NOT NULL,
    requested_version BIGINT NOT NULL,

    CONSTRAINT fk_goods_receipts_order_reservation
        FOREIGN KEY (order_id, reservation_id)
        REFERENCES order_reservations(order_id, id),

    CONSTRAINT ck_goods_receipts_quantity
        CHECK (quantity_kg > 0),

    CONSTRAINT ck_goods_receipts_times
        CHECK (received_at <= recorded_at),

    CONSTRAINT ck_goods_receipts_reference
        CHECK (length(trim(reference)) > 0),

    CONSTRAINT ck_goods_receipts_version
        CHECK (requested_version >= 0)
);

CREATE INDEX idx_goods_receipts_order_recorded
    ON goods_receipts (order_id, recorded_at DESC, id DESC);

CREATE INDEX idx_goods_receipts_reservation
    ON goods_receipts (reservation_id);