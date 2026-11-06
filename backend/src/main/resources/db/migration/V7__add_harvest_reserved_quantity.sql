ALTER TABLE harvest_listings
    ADD COLUMN reserved_quantity_kg NUMERIC(12, 3)
        NOT NULL DEFAULT 0;

ALTER TABLE harvest_listings
    ADD CONSTRAINT ck_harvest_reserved_quantity
        CHECK (
            reserved_quantity_kg >= 0
            AND reserved_quantity_kg <= quantity_kg
        );