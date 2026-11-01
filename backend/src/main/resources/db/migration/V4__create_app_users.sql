CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_app_users_email UNIQUE (email),

    CONSTRAINT ck_app_users_email_normalized
        CHECK (
            email = lower(trim(email))
            AND length(email) > 0
        ),

    CONSTRAINT ck_app_users_display_name
        CHECK (length(trim(display_name)) > 0),

    CONSTRAINT ck_app_users_password_hash
        CHECK (length(trim(password_hash)) > 0)
);

CREATE TABLE app_user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT pk_app_user_roles
        PRIMARY KEY (user_id, role),

    CONSTRAINT fk_app_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_app_user_roles_role
        CHECK (role IN ('ADMIN', 'COORDINATOR', 'FARMER', 'BUYER'))
);

CREATE INDEX idx_app_user_roles_role
    ON app_user_roles (role);