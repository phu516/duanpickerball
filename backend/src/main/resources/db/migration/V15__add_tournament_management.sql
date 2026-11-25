create table tournament_settings (
  id tinyint primary key,
  starts_on date not null,
  ends_on date not null,
  enabled boolean not null,
  updated_at datetime(6) not null
);

create table tournament_registrations (
  id bigint primary key auto_increment,
  full_name varchar(120) not null,
  phone varchar(16) not null,
  email varchar(254),
  category varchar(100),
  note varchar(500),
  created_at datetime(6) not null
);

create index idx_tournament_registrations_created_at
  on tournament_registrations (created_at desc, id desc);
