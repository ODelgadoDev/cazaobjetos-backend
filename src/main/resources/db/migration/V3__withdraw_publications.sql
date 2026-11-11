ALTER TABLE found_item DROP CONSTRAINT found_item_status_check;
ALTER TABLE found_item ADD CONSTRAINT found_item_status_check CHECK (status IN ('AVAILABLE','RETURNED','WITHDRAWN'));
ALTER TABLE lost_report DROP CONSTRAINT lost_report_status_check;
ALTER TABLE lost_report ADD CONSTRAINT lost_report_status_check CHECK (status IN ('ACTIVE','RECOVERED','WITHDRAWN'));
CREATE INDEX found_by_reporter ON found_item(reporter_id);
CREATE INDEX found_by_responsible ON found_item(responsible_id);
CREATE INDEX lost_by_owner ON lost_report(owner_id);
