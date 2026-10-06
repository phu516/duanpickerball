create table carts (
  id bigint auto_increment primary key,
  access_hash varchar(64) not null unique,
  created_at datetime(6) not null,
  updated_at datetime(6) not null
);
create table cart_items (
  id bigint auto_increment primary key,
  cart_id bigint not null,
  variant_id bigint not null,
  quantity int not null,
  constraint fk_cart_item_cart foreign key (cart_id) references carts(id),
  constraint fk_cart_item_variant foreign key (variant_id) references product_variants(id),
  constraint uq_cart_variant unique(cart_id, variant_id)
);

create table orders (
  id bigint auto_increment primary key,
  order_code varchar(40) not null unique,
  user_id bigint,
  booking_id bigint,
  guest_name varchar(160) not null,
  guest_phone varchar(30) not null,
  guest_email varchar(255),
  fulfillment_method varchar(30) not null,
  delivery_address varchar(500),
  assigned_staff_id bigint,
  status varchar(40) not null,
  subtotal decimal(18,2) not null,
  delivery_fee decimal(18,2) not null,
  total_amount decimal(18,2) not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  delivered_at datetime(6),
  constraint fk_order_user foreign key (user_id) references users(id),
  constraint fk_order_staff foreign key (assigned_staff_id) references users(id)
);
create index ix_order_phone on orders(guest_phone);
create index ix_order_status on orders(status, created_at);

create table order_items (
  id bigint auto_increment primary key,
  order_id bigint not null,
  variant_id bigint not null,
  product_name varchar(240) not null,
  sku varchar(80) not null,
  variant_label varchar(120) not null,
  unit_price decimal(18,2) not null,
  quantity int not null,
  constraint fk_item_order foreign key (order_id) references orders(id),
  constraint fk_item_variant foreign key (variant_id) references product_variants(id)
);

create table inventory_reservations (
  id bigint auto_increment primary key,
  order_item_id bigint not null unique,
  variant_id bigint not null,
  quantity int not null,
  status varchar(20) not null,
  expires_at datetime(6),
  constraint fk_reservation_item foreign key (order_item_id) references order_items(id),
  constraint fk_reservation_variant foreign key (variant_id) references product_variants(id)
);

create table inventory_movements (
  id bigint auto_increment primary key,
  variant_id bigint not null,
  quantity_delta int not null,
  reason varchar(250) not null,
  actor_id bigint,
  created_at datetime(6) not null,
  constraint fk_movement_variant foreign key (variant_id) references product_variants(id),
  constraint fk_movement_actor foreign key (actor_id) references users(id)
);
