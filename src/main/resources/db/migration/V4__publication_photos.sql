CREATE TABLE publication_photo (
    id uuid PRIMARY KEY,
    found_item_id uuid REFERENCES found_item(id),
    lost_report_id uuid REFERENCES lost_report(id),
    content_type varchar(20) NOT NULL CHECK (content_type IN ('image/jpeg','image/png')),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    size_bytes integer NOT NULL CHECK (size_bytes = octet_length(content)),
    created_at timestamptz NOT NULL,
    CHECK ((found_item_id IS NOT NULL) <> (lost_report_id IS NOT NULL))
);
CREATE INDEX photos_by_found ON publication_photo(found_item_id,created_at,id);
CREATE INDEX photos_by_lost ON publication_photo(lost_report_id,created_at,id);
