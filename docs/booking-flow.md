# Luồng đặt sân

1. GET `/api/v1/courts` và `/api/v1/court-availability?date=YYYY-MM-DD`.
2. POST `/api/v1/booking-quotes` với `courtId`, `startAt`, `endAt` dạng ISO có offset. Backend từ chối giờ quá khứ, dưới 2 tiếng, lệch mốc 30 phút, giá chưa cấu hình và lịch trùng.
3. POST `/api/v1/guest/bookings` với lựa chọn và `name`, `phone`. Backend khóa hàng `courts` trong transaction, kiểm tra lại lịch và tạo HOLD. Response có `bookingCode`, `guestAccessToken`, `holdExpiresAt`, `depositRequired`.
4. POST `/api/v1/guest/bookings/{code}/payments` với `purpose=DEPOSIT` và token riêng. Sandbox dev xác nhận bằng endpoint dev; khoản cọc thành công chuyển HOLD → CONFIRMED. HOLD quá hạn được job chuyển EXPIRED.
5. Admin check-in; sau mốc 30 phút có thể ghi NO_SHOW. Hủy HOLD tự phục vụ; phiếu CONFIRMED gửi yêu cầu để admin duyệt. Hủy giải phóng lịch nhưng không xóa phiếu.

Hai phiếu của cùng khách độc lập. Sau khi admin thêm sân, phải tạo `court_price_rules` trước khi nhận đặt sân đó.
