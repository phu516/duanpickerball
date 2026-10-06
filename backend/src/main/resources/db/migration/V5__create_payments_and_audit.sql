create table payments (
  id bigint auto_increment primary key,
  order_id bigint,
  booking_id bigint,
  purpose varchar(30) not null,
  method varchar(30) not null,
  provider varchar(40) not null,
  provider_reference varchar(120),
  amount decimal(18,2) not null,
  currency varchar(3) not null,
  status varchar(20) not null,
  created_at datetime(6) not null,
  paid_at datetime(6),
  constraint fk_payment_order foreign key (order_id) references orders(id),
  constraint fk_payment_booking foreign key (booking_id) references bookings(id)
);
create index ix_payment_order on payments(order_id);
create index ix_payment_booking on payments(booking_id);

create table payment_events (
  id bigint auto_increment primary key,
  provider varchar(40) not null,
  event_id varchar(120) not null,
  payment_id bigint not null,
  received_at datetime(6) not null,
  constraint uq_payment_event unique(provider, event_id),
  constraint fk_event_payment foreign key (payment_id) references payments(id)
);

create table refunds (
  id bigint auto_increment primary key,
  payment_id bigint not null,
  amount decimal(18,2) not null,
  reason varchar(500) not null,
  status varchar(20) not null,
  provider_reference varchar(120),
  created_at datetime(6) not null,
  completed_at datetime(6),
  constraint fk_refund_payment foreign key (payment_id) references payments(id)
);

create table settings (
  setting_key varchar(100) primary key,
  setting_value varchar(1000) not null,
  updated_at datetime(6) not null
);
create table audit_logs (
  id bigint auto_increment primary key,
  actor_id bigint,
  action varchar(100) not null,
  resource_type varchar(40) not null,
  resource_id bigint,
  reason varchar(500),
  created_at datetime(6) not null,
  constraint fk_audit_actor foreign key (actor_id) references users(id)
);
create table idempotency_records (
  id bigint auto_increment primary key,
  scope_key varchar(140) not null,
  request_key varchar(120) not null,
  request_hash varchar(64) not null,
  result_type varchar(40) not null,
  result_id bigint not null,
  created_at datetime(6) not null,
  constraint uq_idempotency unique(scope_key, request_key)
);
create table notification_outbox (
  id bigint auto_increment primary key,
  recipient varchar(255) not null,
  template varchar(100) not null,
  payload varchar(4000) not null,
  status varchar(20) not null,
  attempts int not null default 0,
  created_at datetime(6) not null
);
