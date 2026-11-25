alter table users add column full_name varchar(120);
alter table users add column phone varchar(16);
alter table users add column email varchar(254);
alter table users add column email_verified boolean not null default true;
alter table users add column elo_player_id bigint;
alter table users add constraint uq_users_email unique (email);
alter table users add constraint uq_users_elo_player unique (elo_player_id);
alter table users add constraint fk_users_elo_player foreign key (elo_player_id) references elo_players(id);

create table email_verification_tokens (
  id bigint auto_increment primary key,
  user_id bigint not null,
  token_hash varchar(64) not null unique,
  expires_at datetime(6) not null,
  used_at datetime(6),
  created_at datetime(6) not null,
  constraint fk_email_verification_user foreign key (user_id) references users(id)
);

alter table password_reset_tokens add column created_at datetime(6) not null default current_timestamp(6);
