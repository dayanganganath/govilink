ALTER TABLE harvest_listings
    ADD COLUMN collected_quantity_kg NUMERIC(12, 3)
        NOT NULL DEFAULT 0;

ALTER TABLE harvest_listings
    ADD CONSTRAINT ck_harvest_collected_quantity
        CHECK (
            collected_quantity_kg >= 0
            AND reserved_quantity_kg + collected_quantity_kg <= quantity_kg
        );