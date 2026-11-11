ALTER TABLE app_user ADD COLUMN email_verified_at timestamptz;
CREATE TABLE email_verification (
    user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    sent_at timestamptz NOT NULL,
    consumed_at timestamptz
);
ALTER TABLE email_outbox ADD COLUMN attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0);
ALTER TABLE email_outbox ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX pending_email_delivery ON email_outbox(next_attempt_at, created_at) WHERE status = 'PENDING';
