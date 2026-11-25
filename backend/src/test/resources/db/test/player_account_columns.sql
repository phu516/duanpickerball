alter table users add column full_name varchar(120);
alter table users add column phone varchar(16);
alter table users add column email varchar(254);
alter table users add column email_verified boolean default true not null;
alter table users add column elo_player_id bigint;
