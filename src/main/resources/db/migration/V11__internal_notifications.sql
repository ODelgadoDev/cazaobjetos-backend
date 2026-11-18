CREATE TABLE user_notification (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES app_user(id),
    event_key varchar(160) NOT NULL,
    activity_id uuid REFERENCES claim_activity(id),
    kind varchar(40) NOT NULL CHECK(kind IN ('FOUND_PUBLISHED','LOSS_PUBLISHED','SUBMITTED','ACCEPTED','REJECTED','CANCELLED','COMPLETED','CLOSED','COMMENT','EVIDENCE_ADDED','CUSTODY_CHANGED','PUBLICATION_EDITED','DELEGATED','IN_PROGRESS')),
    target_type varchar(20) NOT NULL CHECK(target_type IN ('CLAIM','FOUND_ITEM','LOST_REPORT')),
    target_id uuid NOT NULL,
    title varchar(140) NOT NULL,
    body varchar(500) NOT NULL,
    created_at timestamptz NOT NULL,
    read_at timestamptz,
    cleared_at timestamptz,
    UNIQUE(recipient_id,event_key),
    CHECK(cleared_at IS NULL OR read_at IS NOT NULL)
);
CREATE INDEX notification_inbox ON user_notification(recipient_id,created_at DESC,id DESC) WHERE cleared_at IS NULL;
CREATE INDEX notification_unread ON user_notification(recipient_id) WHERE read_at IS NULL AND cleared_at IS NULL;
-- Introduce existing active cases once, without replaying old messages or pretending they are new.
INSERT INTO user_notification(id,recipient_id,event_key,kind,target_type,target_id,title,body,created_at)
SELECT md5('notification-import:'||c.id||':'||p.recipient)::uuid,p.recipient,'import:claim:'||c.id,'IN_PROGRESS','CLAIM',c.id,
       'Reclamo en curso','Tienes un reclamo en curso. Abre el expediente para consultar su estado actual.',now()
FROM claim c JOIN found_item f ON f.id=c.found_item_id
CROSS JOIN LATERAL (SELECT c.claimant_id AS recipient UNION SELECT f.responsible_id) p
WHERE c.status IN ('PENDING','ACCEPTED') AND f.status='AVAILABLE';
