-- Optional profile data. Existing identity, credentials and roles are unchanged.
-- An absent value is NULL; aliases are display labels, not login identifiers.
ALTER TABLE app_user
    ADD COLUMN alias varchar(60),
    ADD COLUMN classroom varchar(80),
    ADD COLUMN group_name varchar(80),
    ADD COLUMN course varchar(120),
    ADD COLUMN degree varchar(160),
    ADD COLUMN building varchar(80),
    ADD CONSTRAINT user_alias_not_blank CHECK (alias IS NULL OR btrim(alias) <> ''),
    ADD CONSTRAINT user_classroom_not_blank CHECK (classroom IS NULL OR btrim(classroom) <> ''),
    ADD CONSTRAINT user_group_not_blank CHECK (group_name IS NULL OR btrim(group_name) <> ''),
    ADD CONSTRAINT user_course_not_blank CHECK (course IS NULL OR btrim(course) <> ''),
    ADD CONSTRAINT user_degree_not_blank CHECK (degree IS NULL OR btrim(degree) <> ''),
    ADD CONSTRAINT user_building_not_blank CHECK (building IS NULL OR btrim(building) <> '');
