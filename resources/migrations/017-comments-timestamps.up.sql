-- rename the columns
alter table comments
rename column updatedat to updated_at;

alter table comments
rename column createdat to created_at;

-- change the types
alter table comments
alter column updated_at type timestamptz using updated_at at time zone 'America/Edmonton';

alter table comments
alter column created_at type timestamptz using created_at at time zone 'America/Edmonton';

-- populate missing values
update comments
set updated_at = created_at;

-- set non null
alter table comments
alter column updated_at set not null;

-- set auto update trigger
create trigger set_updated_at
before update on comments
for each row
execute procedure trigger_set_updated_at();
