alter table articles
alter column updated_at type timestamptz using updated_at at time zone  'America/Edmonton';

drop trigger if exists set_updated_at on articles;

update articles
set updated_at = created_at;

alter table articles
alter column updated_at set not null;

alter table articles
alter column updated_at set default CURRENT_TIMESTAMP;

CREATE TRIGGER set_updated_at
BEFORE UPDATE ON articles
FOR EACH ROW
EXECUTE PROCEDURE trigger_set_updated_at();
