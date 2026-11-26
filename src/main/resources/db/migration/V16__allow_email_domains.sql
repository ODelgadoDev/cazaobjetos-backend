ALTER TABLE app_user
    DROP CONSTRAINT app_user_email_check,
    ADD CONSTRAINT app_user_email_check
        CHECK (email = lower(email) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$');
