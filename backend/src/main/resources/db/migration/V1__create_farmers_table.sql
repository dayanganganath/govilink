CREATE TABLE farmers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    district VARCHAR(100) NOT NULL,
    village VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT farmers_full_name_not_blank
        CHECK (LENGTH(TRIM(full_name)) > 0),

    CONSTRAINT farmers_phone_not_blank
        CHECK (LENGTH(TRIM(phone)) > 0),

    CONSTRAINT farmers_district_not_blank
        CHECK (LENGTH(TRIM(district)) > 0)
);