CREATE TABLE creation_request (
 actor_id uuid NOT NULL REFERENCES app_user(id),
 scope varchar(100) NOT NULL,
 request_key varchar(100) NOT NULL,
 request_hash varchar(64) NOT NULL,
 resource_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(actor_id,scope,request_key)
);
