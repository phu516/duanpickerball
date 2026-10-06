# Cơ sở dữ liệu

Nguồn schema là các migration V1–V7 trong `backend/src/main/resources/db/migration`.

```mermaid
erDiagram
  categories ||--o{ products : contains
  products ||--|{ product_variants : has
  products ||--o{ product_fulfillment_options : supports
  carts ||--o{ cart_items : contains
  product_variants ||--o{ cart_items : selected
  orders ||--|{ order_items : contains
  product_variants ||--o{ order_items : sold
  orders ||--o{ inventory_reservations : reserves
  courts ||--o{ court_price_rules : priced_by
  courts ||--o{ court_blocks : blocked_by
  courts ||--o{ bookings : booked_on
  bookings ||--o{ booking_cancellation_requests : requests
  bookings |o--o{ orders : delivered_to
  bookings |o--o{ payments : charged
  orders |o--o{ payments : charged
  payments ||--o{ refunds : refunded
  users |o--o{ orders : assigned_staff
```

`bookings` không có `booking_items`: mỗi phiếu là một sân. `orders.booking_id` chỉ dùng khi giao đến sân; tiền hàng vẫn nằm trong `orders`, không cộng vào tiền thuê sân. `payments` phải thuộc đúng một `booking_id` hoặc `order_id`, được kiểm tra khi tạo trong service. `guest_access_tokens` giữ bản băm token; API không trả danh sách phiếu theo điện thoại. Giá bán và giá thuê được chụp vào dòng đơn/phiếu khi tạo, không đổi theo giá hiện hành.
