CREATE TABLE lost_response (
    id uuid PRIMARY KEY,
    lost_report_id uuid NOT NULL REFERENCES lost_report(id),
    finder_id uuid NOT NULL REFERENCES app_user(id),
    location varchar(200) NOT NULL,
    body varchar(2000) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    created_at timestamptz NOT NULL,
    photo_type varchar(20) CHECK (photo_type IN ('image/jpeg', 'image/png')),
    photo bytea CHECK (octet_length(photo) BETWEEN 1 AND 5242880),
    UNIQUE (id, lost_report_id),
    CHECK ((photo_type IS NULL) = (photo IS NULL))
);
CREATE UNIQUE INDEX lost_response_open_finder ON lost_response(lost_report_id,finder_id) WHERE status='OPEN';
CREATE INDEX lost_response_by_report ON lost_response(lost_report_id,created_at DESC,id DESC);

CREATE TABLE lost_response_comment (
    id uuid PRIMARY KEY,
    response_id uuid NOT NULL REFERENCES lost_response(id),
    author_id uuid NOT NULL REFERENCES app_user(id),
    body varchar(2000) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX lost_response_comments ON lost_response_comment(response_id,created_at DESC,id DESC);

ALTER TABLE user_notification
    ADD COLUMN actor_name varchar(120),
    ADD COLUMN object_title varchar(200),
    ADD COLUMN response_id uuid,
    ADD FOREIGN KEY (response_id,target_id) REFERENCES lost_response(id,lost_report_id),
    ADD CHECK(response_id IS NULL OR target_type='LOST_REPORT');

UPDATE user_notification n SET actor_name=a.actor_name,object_title=f.title
FROM claim_activity a JOIN claim c ON c.id=a.claim_id JOIN found_item f ON f.id=c.found_item_id
WHERE n.activity_id=a.id;
UPDATE user_notification n SET object_title=f.title,actor_name=u.display_name
FROM found_item f JOIN app_user u ON u.id=f.reporter_id
WHERE n.target_type='FOUND_ITEM' AND n.target_id=f.id;
UPDATE user_notification n SET object_title=l.title,actor_name=u.display_name
FROM lost_report l JOIN app_user u ON u.id=l.owner_id
WHERE n.target_type='LOST_REPORT' AND n.target_id=l.id;
UPDATE user_notification n SET object_title=f.title,actor_name='Sistema'
FROM claim c JOIN found_item f ON f.id=c.found_item_id
WHERE n.target_type='CLAIM' AND n.target_id=c.id AND n.activity_id IS NULL;

ALTER TABLE claim_evidence ADD COLUMN reference_image boolean NOT NULL DEFAULT false;
