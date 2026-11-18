ALTER TABLE found_item
    DROP CONSTRAINT found_item_custody_check,
    DROP CONSTRAINT found_item_check,
    ADD COLUMN teacher_name varchar(120),
    ADD COLUMN custody_location varchar(200),
    ADD COLUMN custody_marked_by uuid REFERENCES app_user(id),
    ADD COLUMN custody_marked_at timestamptz,
    ADD CONSTRAINT found_custody_values CHECK (custody IN ('WITH_FINDER','WITH_TEACHER','OFFICE')),
    ADD CONSTRAINT found_office_record CHECK (
        (custody='OFFICE' AND office_marked_by IS NOT NULL AND office_marked_at IS NOT NULL)
        OR (custody<>'OFFICE' AND office_marked_by IS NULL AND office_marked_at IS NULL)),
    ADD CONSTRAINT found_teacher_record CHECK (
        (custody='WITH_TEACHER' AND teacher_name IS NOT NULL AND length(btrim(teacher_name))>0)
        OR (custody<>'WITH_TEACHER' AND teacher_name IS NULL)),
    ADD CONSTRAINT found_custody_actor CHECK ((custody_marked_by IS NULL) = (custody_marked_at IS NULL));
UPDATE found_item SET custody_marked_by=office_marked_by,custody_marked_at=office_marked_at
WHERE custody='OFFICE';

CREATE TABLE claim_evidence (
    id uuid PRIMARY KEY,
    claim_id uuid NOT NULL REFERENCES claim(id),
    author_id uuid NOT NULL REFERENCES app_user(id),
    content_type varchar(20) NOT NULL CHECK (content_type IN ('image/jpeg','image/png')),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    size_bytes integer NOT NULL CHECK (size_bytes=octet_length(content)),
    created_at timestamptz NOT NULL
);
CREATE INDEX evidence_by_claim ON claim_evidence(claim_id,created_at,id);

CREATE TABLE claim_activity (
    id uuid PRIMARY KEY,
    sequence bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    claim_id uuid NOT NULL REFERENCES claim(id),
    actor_id uuid REFERENCES app_user(id),
    actor_name varchar(120) NOT NULL,
    kind varchar(40) NOT NULL CHECK (kind IN ('SUBMITTED','ACCEPTED','REJECTED','CANCELLED','COMPLETED','CLOSED','COMMENT','EVIDENCE_ADDED','CUSTODY_CHANGED','PUBLICATION_EDITED','DELEGATED','HISTORY_IMPORTED')),
    body varchar(4000) NOT NULL,
    details_json text,
    evidence_id uuid REFERENCES claim_evidence(id),
    request_key varchar(100),
    request_hash varchar(64),
    created_at timestamptz NOT NULL,
    UNIQUE(claim_id,actor_id,kind,request_key),
    CHECK ((request_key IS NULL) = (request_hash IS NULL)),
    CHECK ((kind='EVIDENCE_ADDED' AND evidence_id IS NOT NULL) OR (kind<>'EVIDENCE_ADDED' AND evidence_id IS NULL))
);
CREATE INDEX activity_by_claim ON claim_activity(claim_id,created_at DESC,sequence DESC);

-- Preserve facts already present. Old publications are not presented as original snapshots.
INSERT INTO claim_activity(id,claim_id,actor_id,actor_name,kind,body,created_at)
SELECT md5('claim-submitted:'||c.id)::uuid,c.id,c.claimant_id,u.display_name,'SUBMITTED',
       'Reclamo registrado.',c.created_at
FROM claim c JOIN app_user u ON u.id=c.claimant_id;
INSERT INTO claim_activity(id,claim_id,actor_id,actor_name,kind,body,created_at)
SELECT a.id,c.id,a.actor_id,u.display_name,
       CASE a.action WHEN 'CLAIM_ACCEPT' THEN 'ACCEPTED' WHEN 'CLAIM_REJECT' THEN 'REJECTED'
            WHEN 'CLAIM_CANCEL' THEN 'CANCELLED' ELSE 'COMPLETED' END,
       CASE a.action WHEN 'CLAIM_ACCEPT' THEN 'El responsable aceptó el reclamo.'
            WHEN 'CLAIM_REJECT' THEN 'El responsable rechazó el reclamo.'
            WHEN 'CLAIM_CANCEL' THEN 'El solicitante canceló el reclamo.'
            ELSE 'El solicitante confirmó que recibió el objeto.' END,a.created_at
FROM audit_event a JOIN claim c ON c.id=a.entity_id JOIN app_user u ON u.id=a.actor_id
WHERE a.action IN ('CLAIM_ACCEPT','CLAIM_REJECT','CLAIM_CANCEL','CLAIM_COMPLETE')
ORDER BY a.created_at,a.id;
INSERT INTO claim_activity(id,claim_id,actor_id,actor_name,kind,body,created_at)
SELECT md5('claim-auto-closed:'||c.id)::uuid,c.id,NULL,'Sistema','CLOSED',
       'El hallazgo se entregó y esta solicitud quedó cerrada.',winner.completed_at
FROM claim c JOIN claim winner ON winner.found_item_id=c.found_item_id AND winner.status='COMPLETED'
WHERE c.status='CLOSED';
INSERT INTO claim_activity(id,claim_id,actor_id,actor_name,kind,body,details_json,created_at)
SELECT md5('claim-imported:'||c.id)::uuid,c.id,NULL,'Sistema','HISTORY_IMPORTED',
       'Expediente anterior: esta ficha se capturó al actualizar el sistema, no al crear el reclamo.',
       jsonb_build_object('foundItem',jsonb_build_object('title',f.title,'description',f.description,
          'foundLocation',f.found_location,'foundOn',f.found_on,'custody',f.custody),
          'claimStatus',c.status,'capturedAt',now(),'imported',true)::text,now()
FROM claim c JOIN found_item f ON f.id=c.found_item_id;
