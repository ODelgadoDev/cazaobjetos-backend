-- Execute with psql -v email='verified@utch.edu.mx' -v new_role='TEACHER' -f scripts/set-role.sql
-- Requires privileged database access. No public API grants these permissions.
\set ON_ERROR_STOP on
BEGIN;
SELECT EXISTS(SELECT 1 FROM app_user WHERE email=lower(btrim(:'email'))
    AND enabled AND email_verified_at IS NOT NULL)
    AND :'new_role' IN ('STUDENT','TEACHER','SECRETARY') AS valid \gset
\if :valid
WITH previous AS (
    SELECT id,role FROM app_user WHERE email=lower(btrim(:'email'))
        AND enabled AND email_verified_at IS NOT NULL FOR UPDATE
), changed AS (
    UPDATE app_user u SET role=:'new_role',credentials_version=credentials_version+1
    FROM previous p WHERE u.id=p.id AND u.role<>:'new_role'
    RETURNING u.id,p.role AS old_role,u.role AS new_role
)
INSERT INTO account_role_audit(user_id,database_actor,old_role,new_role)
SELECT id,current_user,old_role,new_role FROM changed
RETURNING user_id,old_role,new_role;
COMMIT;
\else
ROLLBACK;
DO $$ BEGIN RAISE EXCEPTION 'No se modificó la cuenta: se requiere correo verificado, cuenta habilitada y rol válido.'; END $$;
\endif
