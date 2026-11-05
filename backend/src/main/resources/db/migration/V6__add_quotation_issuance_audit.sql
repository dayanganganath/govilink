ALTER TABLE quotations
    ADD COLUMN issued_by UUID REFERENCES app_users(id),
    ADD COLUMN issued_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE quotations
    ADD CONSTRAINT ck_quotations_issuance_pair
        CHECK (
            (issued_by IS NULL AND issued_at IS NULL)
            OR
            (issued_by IS NOT NULL AND issued_at IS NOT NULL)
        ),

    ADD CONSTRAINT ck_quotations_issuance_status
        CHECK (
            (status = 'DRAFT' AND issued_by IS NULL)
            OR status = 'CANCELLED'
            OR (
                status IN (
                    'ISSUED',
                    'ACCEPTED',
                    'REJECTED',
                    'EXPIRED'
                )
                AND issued_by IS NOT NULL
            )
        ),

    ADD CONSTRAINT ck_quotations_issued_expiry
        CHECK (
            issued_at IS NULL
            OR (
                expires_at IS NOT NULL
                AND expires_at > issued_at
            )
        );

CREATE INDEX idx_quotations_issued_by
    ON quotations (issued_by);