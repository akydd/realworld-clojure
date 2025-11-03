alter table articles
alter column updated_at drop default;

alter table articles
alter column updated_at drop not null;

alter table articles
alter column updated_at type timestamp;
