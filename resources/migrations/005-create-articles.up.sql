create table if not exists articles (
	id serial primary key,
	slug varchar(100) not null unique,
	title varchar(100) not null unique,
	description varchar(100) not null,
	body text not null,
	createdAt timestamp not null,
	updatedAt timestamp,
	author integer references users
);
