create table if not exists article_tags (
       article integer references articles,
       tag integer references tags,
       primary key (article, tag)
);
