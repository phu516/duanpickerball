alter table payments add constraint chk_payment_owner check (
  (booking_id is null and order_id is not null) or
  (booking_id is not null and order_id is null)
);
alter table product_variants add constraint chk_variant_stock check (
  stock_on_hand >= 0 and stock_reserved >= 0 and stock_on_hand >= stock_reserved
);
alter table bookings add constraint chk_booking_interval check (end_at > start_at);
alter table inventory_reservations add constraint chk_reservation_quantity check (quantity > 0);
