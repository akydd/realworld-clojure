-- drop trigger
drop trigger if exists set_updated_at on comments;

-- make column nullable
alter table comments
alter column updated_at drop not null;

-- change types
alter table comments
alter column updated_at type timestamp;

alter table comments
alter column created_at type timestamp;

-- change names
alter table comments
rename column updated_at to updatedat;

alter table comments
rename column created_at to createdat;
