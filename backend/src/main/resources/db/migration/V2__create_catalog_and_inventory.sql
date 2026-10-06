create table categories (
  id bigint auto_increment primary key,
  name varchar(120) not null unique,
  slug varchar(120) not null unique,
  sort_order int not null default 0,
  active boolean not null default true
);

create table products (
  id bigint auto_increment primary key,
  category_id bigint not null,
  name varchar(240) not null,
  brand varchar(120) not null,
  description varchar(2000),
  image_url varchar(500),
  emoji varchar(12) not null,
  active boolean not null default true,
  created_at datetime(6) not null,
  constraint fk_product_category foreign key (category_id) references categories(id)
);
create index ix_products_category on products(category_id, active);

create table product_variants (
  id bigint auto_increment primary key,
  product_id bigint not null,
  sku varchar(80) not null unique,
  label varchar(120) not null,
  price decimal(18,2) not null,
  stock_on_hand int not null default 0,
  stock_reserved int not null default 0,
  active boolean not null default true,
  constraint fk_variant_product foreign key (product_id) references products(id)
);

create table product_fulfillment_options (
  id bigint auto_increment primary key,
  product_id bigint not null,
  method varchar(30) not null,
  constraint fk_fulfillment_product foreign key (product_id) references products(id),
  constraint uq_product_fulfillment unique (product_id, method)
);

create table product_images (
  id bigint auto_increment primary key,
  product_id bigint not null,
  variant_id bigint,
  url varchar(500) not null,
  sort_order int not null default 0,
  primary_image boolean not null default false,
  constraint fk_image_product foreign key (product_id) references products(id),
  constraint fk_image_variant foreign key (variant_id) references product_variants(id)
);
