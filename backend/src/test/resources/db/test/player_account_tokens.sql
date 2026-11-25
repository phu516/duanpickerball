alter table users add column full_name varchar(120);
alter table users add column phone varchar(16);
alter table users add column email varchar(254) unique;
alter table users add column email_verified boolean default true not null;
alter table users add column elo_player_id bigint unique;
alter table password_reset_tokens add column created_at timestamp default current_timestamp not null;
alter table password_reset_tokens add column otp_nonce varchar(32);
alter table password_reset_tokens add column attempts int default 0 not null;
alter table password_reset_tokens add column verified_at timestamp;
alter table password_reset_tokens add column reset_ticket_hash varchar(64) unique;
alter table password_reset_tokens add column reset_ticket_expires_at timestamp;
create table email_verification_tokens (
  id bigint auto_increment primary key,
  user_id bigint not null,
  token_hash varchar(64) not null unique,
  otp_nonce varchar(32),
  attempts int default 0 not null,
  expires_at timestamp not null,
  used_at timestamp,
  created_at timestamp not null
);
