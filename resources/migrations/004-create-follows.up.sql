create table if not exists follows (
	user_id integer references users,
	follows integer references users,
	primary key (user_id, follows)
);
