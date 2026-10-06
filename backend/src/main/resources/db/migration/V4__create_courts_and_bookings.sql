create table courts (
  id bigint auto_increment primary key,
  code varchar(20) not null unique,
  name varchar(120) not null,
  surface varchar(120) not null,
  indoor boolean not null default false,
  description varchar(2000),
  image_url varchar(500),
  status varchar(20) not null
);

create table court_price_rules (
  id bigint auto_increment primary key,
  court_id bigint not null,
  day_of_week int not null,
  start_time time not null,
  end_time time not null,
  hourly_price decimal(18,2) not null,
  valid_from date not null,
  valid_to date,
  constraint fk_price_court foreign key (court_id) references courts(id)
);
create index ix_price_rule on court_price_rules(court_id, day_of_week, valid_from);

create table court_blocks (
  id bigint auto_increment primary key,
  court_id bigint not null,
  start_at datetime(6) not null,
  end_at datetime(6) not null,
  reason varchar(250) not null,
  active boolean not null default true,
  created_by bigint,
  created_at datetime(6) not null,
  constraint fk_block_court foreign key (court_id) references courts(id),
  constraint fk_block_user foreign key (created_by) references users(id)
);
create index ix_block_time on court_blocks(court_id, start_at, end_at);

create table bookings (
  id bigint auto_increment primary key,
  booking_code varchar(40) not null unique,
  court_id bigint not null,
  user_id bigint,
  guest_name varchar(160) not null,
  guest_phone varchar(30) not null,
  guest_email varchar(255),
  note varchar(500),
  start_at datetime(6) not null,
  end_at datetime(6) not null,
  total_amount decimal(18,2) not null,
  deposit_required decimal(18,2) not null,
  price_snapshot varchar(2000) not null,
  policy_snapshot varchar(2000) not null,
  status varchar(30) not null,
  hold_expires_at datetime(6) not null,
  minimum_duration_minutes_snapshot int not null,
  late_arrival_grace_minutes_snapshot int not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  cancelled_at datetime(6),
  cancel_reason varchar(500),
  no_show_at datetime(6),
  no_show_fee decimal(18,2),
  constraint fk_booking_court foreign key (court_id) references courts(id),
  constraint fk_booking_user foreign key (user_id) references users(id)
);
create index ix_booking_time on bookings(court_id, start_at, end_at);
create index ix_booking_hold on bookings(status, hold_expires_at);
create index ix_booking_phone on bookings(guest_phone);

create table booking_cancellation_requests (
  id bigint auto_increment primary key,
  booking_id bigint not null,
  reason varchar(500) not null,
  status varchar(20) not null,
  reviewed_by bigint,
  review_reason varchar(500),
  created_at datetime(6) not null,
  reviewed_at datetime(6),
  constraint fk_cancel_booking foreign key (booking_id) references bookings(id),
  constraint fk_cancel_reviewer foreign key (reviewed_by) references users(id)
);
alter table orders add constraint fk_order_booking foreign key (booking_id) references bookings(id);
