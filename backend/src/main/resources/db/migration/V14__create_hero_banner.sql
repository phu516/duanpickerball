create table hero_banner (
  id tinyint primary key,
  title varchar(120) not null,
  summary varchar(300) not null,
  event_start_at datetime(6) not null,
  visible_from datetime(6) not null,
  visible_until datetime(6) not null,
  link_url varchar(500),
  image_content_type varchar(30) not null,
  image_data mediumblob not null,
  enabled boolean not null,
  updated_at datetime(6) not null
);
