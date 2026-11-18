ALTER TABLE found_item ADD COLUMN source_lost_report_id uuid REFERENCES lost_report(id);
CREATE INDEX found_source_lost ON found_item(source_lost_report_id) WHERE source_lost_report_id IS NOT NULL;
ALTER TABLE user_notification DROP CONSTRAINT user_notification_kind_check;
ALTER TABLE user_notification ADD CONSTRAINT user_notification_kind_check CHECK(kind IN ('FOUND_PUBLISHED','LOSS_PUBLISHED','SUBMITTED','ACCEPTED','REJECTED','CANCELLED','COMPLETED','CLOSED','COMMENT','EVIDENCE_ADDED','CUSTODY_CHANGED','PUBLICATION_EDITED','DELEGATED','IN_PROGRESS','POSSIBLE_MATCH'));
-- Existing publications remain unlinked. No historical notifications or emails are replayed.
