-- A single replaceable avatar per account, separate from publication photographs.
CREATE TABLE user_profile_photo (
    user_id uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    version uuid NOT NULL UNIQUE,
    content_type varchar(20) NOT NULL CHECK (content_type = 'image/jpeg'),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    size_bytes integer NOT NULL CHECK (size_bytes = octet_length(content)),
    updated_at timestamptz NOT NULL
);
