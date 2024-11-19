create table if not exists comments (
	id serial primary key,
	body text not null,
	article integer references articles,
	author integer references users,
	createdAt timestamp not null,
	updatedAt timestamp
);
