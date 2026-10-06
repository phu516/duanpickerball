# Kiểm tra nghiệm thu local

- [x] Backend biên dịch bằng `javac`; profile dev khởi động và chạy Flyway V1–V7; Datafaker tạo 6 sân/10 sản phẩm.
- [x] Khách tạo HOLD không đăng nhập, cọc sandbox thành công → CONFIRMED.
- [x] Khách tạo giỏ/đơn `STORE_PICKUP`; tổng đơn bằng số lượng × giá SKU.
- [x] Admin đăng nhập qua phiên và đọc báo cáo tổng quan; khách không có phiên không được vào `/admin/**`.
- [x] React production build bằng Vite.
- [x] `scripts/acceptance-smoke.ps1`: nhiều phiếu độc lập, lịch nối tiếp/trùng, tối thiểu 2 giờ, thiếu token, hủy một phiếu, cọc và checkout.
- [x] Maven Wrapper chạy test tích hợp hai yêu cầu đặt cùng sân/giờ: chỉ một thành công.
- [x] Maven test tích hợp hai đơn cùng tranh SKU cuối: đúng một đơn giữ hàng, tồn không âm.
- [x] Maven test thu cọc thủ công, tạo/duyệt khoản hoàn, chặn hoàn vượt số tiền đã thu.
- [x] Retry cùng `Idempotency-Key` không tạo thêm phiếu/đơn; đã kiểm tra bằng bài test tích hợp và smoke HTTP.
- [x] Backend và Vite chạy đồng thời ở `localhost:8080` và `localhost:5173` sau lần build mới nhất.
- [x] Giao diện gọi API thành công khi mở bằng `localhost:5173` và `127.0.0.1:5173`; form sân tính được giá/cọc.
- [x] MySQL 8.4 chạy trong Docker ở cổng 3307; Flyway tạo schema `picklezone`, áp dụng đủ V1–V7 và JPA `validate` khởi động thành công.
- [x] Profile `mysql-seed` nạp bổ sung dữ liệu Faker vào MySQL; sau cập nhật có 6 sân, 20 sản phẩm, 4 danh mục và 9 phiếu; API và giao diện đọc dữ liệu từ database.
- [x] Profile `mysql-seed` tạo tài khoản `admin` và `staff` khi cấu hình biến mật khẩu; xác nhận 2 dòng trong `users` và đăng nhập admin thành công.
- [ ] Kết nối cổng thanh toán có webhook ký và dịch vụ email/SMS khi chọn nhà cung cấp.
- [ ] Kiểm tra trình duyệt toàn bộ màn hình ở nhiều kích thước và đi hết luồng giao đến sân bằng nhân viên.
