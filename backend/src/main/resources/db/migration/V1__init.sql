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

CREATE TABLE transactions
(
    id          UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id  UUID           NOT NULL,
    category_id UUID,
    kind        TEXT           NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    date        DATE           NOT NULL,
    label       TEXT           NOT NULL,
    note        TEXT,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT transactions_kind_chk CHECK (kind IN ('DEPENSE', 'REVENU')),
    CONSTRAINT transactions_amount_chk CHECK (amount > 0),
    CONSTRAINT transactions_account_fk FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id) ON DELETE NO ACTION,
    CONSTRAINT transactions_category_fk FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id) ON DELETE SET NULL (category_id)
);

CREATE TABLE budgets
(
    id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  UUID          NOT NULL,
    month        date          NOT NULL,
    amount_limit NUMERIC(12,2) NOT NULL,
    CONSTRAINT budgets_category_fk FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id) ON DELETE CASCADE,
    CONSTRAINT budgets_user_category_month_uq UNIQUE (user_id, category_id, month),
    CONSTRAINT budgets_amount_limit_chk CHECK (amount_limit > 0),
    CONSTRAINT budgets_month_chk CHECK (EXTRACT(DAY FROM month) = 1)
);

CREATE INDEX transactions_user_date_idx on transactions (user_id, date);
CREATE INDEX transactions_account_idx on transactions (account_id);
CREATE INDEX transactions_category_idx on transactions (category_id);
CREATE INDEX budgets_category_idx on budgets (category_id);