ALTER TABLE lost_response
    ADD COLUMN custody varchar(20) NOT NULL DEFAULT 'WITH_FINDER'
        CHECK (custody IN ('WITH_FINDER','OFFICE')),
    ADD COLUMN custody_location varchar(200),
    ADD CONSTRAINT lost_response_custody_location CHECK (
        (custody='WITH_FINDER' AND custody_location IS NULL) OR
        (custody='OFFICE' AND custody_location IS NOT NULL AND length(btrim(custody_location))>0)
    );
