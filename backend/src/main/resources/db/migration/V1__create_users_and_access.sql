create table users (
  id bigint auto_increment primary key,
  username varchar(80) not null unique,
  password_hash varchar(100) not null,
  role varchar(20) not null,
  enabled boolean not null default true,
  created_at datetime(6) not null
);

create table guest_access_tokens (
  id bigint auto_increment primary key,
  resource_type varchar(20) not null,
  resource_id bigint not null,
  token_hash varchar(64) not null unique,
  expires_at datetime(6) not null,
  revoked_at datetime(6),
  created_at datetime(6) not null
);
create index ix_guest_access_resource on guest_access_tokens(resource_type, resource_id);

create table access_challenges (
  id bigint auto_increment primary key,
  resource_type varchar(20) not null,
  resource_id bigint not null,
  code_hash varchar(64) not null,
  expires_at datetime(6) not null,
  attempts int not null default 0,
  used_at datetime(6)
);

create table password_reset_tokens (
  id bigint auto_increment primary key,
  user_id bigint not null,
  token_hash varchar(64) not null unique,
  expires_at datetime(6) not null,
  used_at datetime(6),
  constraint fk_reset_user foreign key (user_id) references users(id)
);
