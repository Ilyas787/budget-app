CREATE TABLE users
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_uq UNIQUE (email),
    CONSTRAINT users_email_lower_chk CHECK (lower(email) = email)
);

CREATE TABLE accounts
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            TEXT           NOT NULL,
    type            TEXT           NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'EUR',
    initial_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT accounts_name_uq UNIQUE (user_id, name),
    CONSTRAINT accounts_type_chk CHECK (type IN ('COURANT', 'EPARGNE', 'ESPECES')),
    CONSTRAINT accounts_id_user_uq UNIQUE (id, user_id)
);

CREATE TABLE categories
(
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name    TEXT NOT NULL,
    kind    TEXT NOT NULL,
    color   TEXT,
    CONSTRAINT categories_name_uq UNIQUE (user_id, name),
    CONSTRAINT categories_kind_chk CHECK (kind IN ('DEPENSE', 'REVENU')),
    CONSTRAINT categories_id_user_uq UNIQUE (id, user_id)
);