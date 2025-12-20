alter table articles
alter column author set not null;

alter table comments
alter column article set not null;

alter table comments
alter column author set not null;
