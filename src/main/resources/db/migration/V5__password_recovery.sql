ALTER TABLE app_user ADD COLUMN credentials_version bigint NOT NULL DEFAULT 0;
CREATE TABLE password_reset (
 user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 token_hash varchar(64) NOT NULL UNIQUE,
 expires_at timestamptz NOT NULL,
 sent_at timestamptz NOT NULL,
 consumed_at timestamptz
);
CREATE TABLE account_role_audit (
 id bigserial PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES app_user(id),
 database_actor text NOT NULL,
 old_role varchar(20) NOT NULL,
 new_role varchar(20) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
