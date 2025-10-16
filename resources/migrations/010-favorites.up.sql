create table if not exists favorites (
       user_id integer references users,
       article integer references articles,
       primary key (user_id, article)
);
