create table elo_players (
  id bigint primary key auto_increment,
  full_name varchar(120) not null,
  phone varchar(16) not null unique,
  experience varchar(24) not null,
  pickleball_months int,
  other_sport varchar(120),
  face_embedding text,
  rating int not null default 1000,
  games_played int not null default 0,
  created_at datetime(6) not null
);

create table elo_matches (
  id bigint primary key auto_increment,
  player_a_id bigint not null,
  player_b_id bigint not null,
  status varchar(20) not null,
  score_a int,
  score_b int,
  created_at datetime(6) not null,
  confirmed_at datetime(6),
  foreign key (player_a_id) references elo_players(id),
  foreign key (player_b_id) references elo_players(id)
);

create index idx_elo_matches_players on elo_matches(player_a_id, player_b_id);
