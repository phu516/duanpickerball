# Luồng đặt sân

1. GET `/api/v1/courts` và `/api/v1/court-availability?date=YYYY-MM-DD`.
2. POST `/api/v1/booking-quotes` với `courtId`, `startAt`, `endAt` dạng ISO có offset. Backend từ chối giờ quá khứ, dưới 2 tiếng, lệch mốc 30 phút, giá chưa cấu hình và lịch trùng.
3. POST `/api/v1/guest/bookings` với lựa chọn và `name`, `phone`. Backend khóa hàng `courts` trong transaction, kiểm tra lại lịch và tạo HOLD. Response có `bookingCode`, `guestAccessToken`, `holdExpiresAt`, `depositRequired`.
4. POST `/api/v1/guest/bookings/{code}/payments` với `purpose=DEPOSIT` và token riêng. Sandbox dev xác nhận bằng endpoint dev; khoản cọc thành công chuyển HOLD → CONFIRMED. HOLD quá hạn được job chuyển EXPIRED.
   Giao diện giữ khách ở màn hình thanh toán khi còn HOLD; chỉ mở chi tiết phiếu và hiển thị trong danh sách quản lý sau khi khoản cọc thành công.
5. Admin có thể nhận sân từ 30 phút trước giờ bắt đầu cho phiếu đã xác nhận; sau mốc đến trễ 30 phút có thể ghi NO_SHOW. Khách không tự hủy phiếu và không gửi yêu cầu hủy qua website; nếu muốn hủy, khách liên hệ trực tiếp admin để trao đổi. Admin là bên duy nhất có quyền hủy, thao tác hủy giải phóng lịch nhưng không xóa phiếu.
   Phiếu do admin hủy hiển thị trong tab quản lý hủy sân cùng lý do và thông tin khách, sân, thời gian, tiền sân và tiền cọc; phiếu đã hủy được ẩn khỏi danh sách Phiếu sân.

Hai phiếu của cùng khách độc lập. Sau khi admin thêm sân, phải tạo `court_price_rules` trước khi nhận đặt sân đó.
