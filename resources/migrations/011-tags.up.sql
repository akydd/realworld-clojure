create table if not exists tags (
       id serial primary key,
       tag varchar(100) not null unique
);
