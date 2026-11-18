CREATE TABLE push_subscription (
 id uuid PRIMARY KEY, recipient_id uuid NOT NULL REFERENCES app_user(id), endpoint text NOT NULL CHECK(length(endpoint)<=2048), endpoint_hash varchar(64) NOT NULL UNIQUE,
 p256dh varchar(100) NOT NULL, auth_secret varchar(30) NOT NULL, binding_token varchar(43) NOT NULL, session_hash varchar(64) NOT NULL,
 credentials_version bigint NOT NULL, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL, last_seen_at timestamptz NOT NULL
);
CREATE INDEX push_active_user ON push_subscription(recipient_id) WHERE active;
CREATE INDEX push_session ON push_subscription(session_hash) WHERE active;
CREATE TABLE push_preference (recipient_id uuid PRIMARY KEY REFERENCES app_user(id), daily_reminders integer NOT NULL DEFAULT 1 CHECK(daily_reminders BETWEEN 0 AND 2));
CREATE TABLE push_delivery (
 id uuid PRIMARY KEY, subscription_id uuid NOT NULL REFERENCES push_subscription(id), recipient_id uuid NOT NULL REFERENCES app_user(id), binding_token varchar(43) NOT NULL,
 notification_id uuid REFERENCES user_notification(id), kind varchar(10) NOT NULL CHECK(kind IN ('EVENT','REMINDER','TEST')), event_key varchar(160) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','CANCELLED','FAILED')), attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, next_attempt_at timestamptz NOT NULL, sent_at timestamptz, last_status integer,
 UNIQUE(subscription_id,binding_token,event_key), CHECK((kind='EVENT')=(notification_id IS NOT NULL))
);
CREATE INDEX push_due ON push_delivery(next_attempt_at,created_at) WHERE status='PENDING';
CREATE TABLE push_reminder_run (recipient_id uuid NOT NULL REFERENCES app_user(id), local_date date NOT NULL, slot integer NOT NULL CHECK(slot IN (1,2)), created_at timestamptz NOT NULL, PRIMARY KEY(recipient_id,local_date,slot));
-- No historical push is queued. Existing accounts use one daily reminder when they opt in on a device.
