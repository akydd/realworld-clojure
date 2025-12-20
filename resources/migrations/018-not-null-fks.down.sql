alter table articles
alter column author drop not null;

alter table comments
alter column article drop not null;

alter table comments
alter column author drop not null;
