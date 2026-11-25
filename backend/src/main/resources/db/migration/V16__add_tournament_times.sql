alter table tournament_settings
  add column starts_time time(6) not null default '00:00:00';

alter table tournament_settings
  add column ends_time time(6) not null default '23:59:59.999999';
