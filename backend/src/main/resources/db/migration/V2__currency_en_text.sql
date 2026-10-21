ALTER TABLE accounts ALTER COLUMN currency TYPE TEXT;
ALTER TABLE accounts ADD CONSTRAINT accounts_currency_chk CHECK (currency ~ '^[A-Z]{3}$');