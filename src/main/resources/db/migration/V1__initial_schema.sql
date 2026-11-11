CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(email) AND email LIKE '%@utch.edu.mx'),
    password_hash varchar(100) NOT NULL,
    display_name varchar(120) NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'SECRETARY')),
    enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE found_item (
    id uuid PRIMARY KEY,
    reporter_id uuid NOT NULL REFERENCES app_user(id),
    responsible_id uuid NOT NULL REFERENCES app_user(id),
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    category varchar(30) NOT NULL CHECK (category IN ('ELECTRONICS','DOCUMENTS','KEYS','CLOTHING','OTHER')),
    found_location varchar(200) NOT NULL,
    found_on date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','RETURNED')),
    custody varchar(20) NOT NULL DEFAULT 'WITH_FINDER' CHECK (custody IN ('WITH_FINDER','OFFICE')),
    office_marked_by uuid REFERENCES app_user(id),
    office_marked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((custody = 'OFFICE' AND office_marked_by IS NOT NULL AND office_marked_at IS NOT NULL)
        OR (custody = 'WITH_FINDER' AND office_marked_by IS NULL AND office_marked_at IS NULL))
);

CREATE TABLE lost_report (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_user(id),
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    category varchar(30) NOT NULL CHECK (category IN ('ELECTRONICS','DOCUMENTS','KEYS','CLOTHING','OTHER')),
    lost_location varchar(200) NOT NULL,
    lost_on date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RECOVERED')),
    recovered_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE claim (
    id uuid PRIMARY KEY,
    found_item_id uuid NOT NULL REFERENCES found_item(id),
    claimant_id uuid NOT NULL REFERENCES app_user(id),
    lost_report_id uuid REFERENCES lost_report(id),
    private_description varchar(2000) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','COMPLETED','CLOSED')),
    accepted_by uuid REFERENCES app_user(id),
    accepted_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX one_active_claim_per_user ON claim(found_item_id, claimant_id) WHERE status IN ('PENDING','ACCEPTED');
CREATE UNIQUE INDEX one_winning_claim ON claim(found_item_id) WHERE status IN ('ACCEPTED','COMPLETED');
CREATE UNIQUE INDEX one_active_linked_loss ON claim(lost_report_id) WHERE lost_report_id IS NOT NULL AND status IN ('PENDING','ACCEPTED');
CREATE INDEX found_catalog ON found_item(created_at DESC, id);
CREATE INDEX lost_catalog ON lost_report(created_at DESC, id);
CREATE INDEX claims_by_claimant ON claim(claimant_id);

CREATE TABLE audit_event (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES app_user(id),
    entity_id uuid NOT NULL,
    action varchar(60) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Outbox: committed together with recovery; delivery worker is a later increment.
CREATE TABLE email_outbox (
    id uuid PRIMARY KEY,
    event_key varchar(100) NOT NULL UNIQUE,
    recipient varchar(254) NOT NULL,
    subject varchar(200) NOT NULL,
    body varchar(4000) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz
);
