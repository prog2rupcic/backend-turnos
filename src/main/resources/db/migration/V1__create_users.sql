CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    image_url VARCHAR(256),
    lang_key VARCHAR(10) NOT NULL DEFAULT 'es',
    activated BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_authority (
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    authority VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, authority)
);
