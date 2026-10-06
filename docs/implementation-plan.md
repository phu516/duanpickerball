# Kế hoạch triển khai theo PLAN_DU_AN_PICKLEBALL.md

Tài liệu này là thứ tự thực hiện cho mã nguồn hiện có. Mục 4.2 của plan là cây thư mục đích; chỉ tạo thư mục khi có mã hoặc tài liệu thực sự dùng. Mọi API mới dùng tiền tố `/api/v1`.

## 1. Sửa nền tảng và cấu trúc

- Đổi Java package thành `com.pickleball`; mỗi nghiệp vụ có controller, DTO, entity, repository và service riêng khi cần. Xóa controller gom mọi API.
- Chuyển React sang `app`, `layouts`, `features`, `shared`, `assets`; URL khớp mục 12 của plan. API frontend gọi qua một client dùng chung.
- Khóa phụ thuộc bằng Maven/`package-lock.json`, thêm `.env.example`, README từng ứng dụng và tài liệu trong `docs`.
- Đưa schema vào Flyway V1–V5; cấu hình MySQL làm cơ sở dữ liệu chính, H2 chỉ cho dev/test. Seeder Datafaker chạy ở profile dev và có thể chạy lại an toàn.

## 2. Quyền truy cập và dữ liệu

- Admin/nhân viên đăng nhập bằng Spring Security session, phân quyền theo vai trò; thao tác thay đổi dùng CSRF. Không để `/admin/**` công khai.
- Khách tạo booking/order không cần tài khoản. Khi tạo, trả token ngẫu nhiên và chỉ lưu SHA-256 trong DB. Đọc/hủy/thanh toán yêu cầu token của đúng tài nguyên; mã phiếu và số điện thoại không phải mật khẩu.
- DTO response không trả số điện thoại/email của khách trên API lịch công khai. Tiền dùng `BigDecimal` và giá được chụp vào booking/order item.

## 3. Các luồng nghiệp vụ

1. Danh mục → sản phẩm → SKU/giá/tồn → ảnh/cách nhận. Nước uống dùng chung `products`.
2. Giỏ khách có quyền riêng → quote → checkout → order riêng với booking → giữ hàng → admin/nhân viên xử lý → hủy và trả hàng giữ.
3. Sân → quy tắc giá → lịch khóa → quote → booking HOLD 30 phút → thanh toán cọc sandbox → check-in/hoàn tất/no-show/hủy. Mỗi phiếu chỉ một sân, khách tạo nhiều phiếu.
4. Giao đến sân kiểm tra token booking, khung giờ, hình thức nhận của tất cả sản phẩm; staff chỉ thấy đơn được phân công và ghi nhận đã giao.
5. Ghi payments/refunds độc lập với trạng thái phiếu/đơn; webhook sandbox có xác minh và chống sự kiện trùng. Báo cáo tách giá trị đơn khỏi tiền thực thu.

## 4. Giao diện và kiểm thử

- Giữ ngôn ngữ thiết kế PickleZone từ Stitch. Tách trang khách/admin; mọi form hiển thị loading, lỗi, rỗng và kết quả.
- Thử các tình huống trọng tâm trong mục 13: đặt trùng đồng thời, giờ nối tiếp, HOLD hết hạn, token sai, tồn món cuối, giá chụp, thêm sân số 7, chặn no-show sớm, giao đến sân và đối soát cọc.
- Bản chạy local có hướng dẫn MySQL, tài khoản admin dev cấp qua biến môi trường, dữ liệu Faker và giới hạn rõ các tích hợp phụ thuộc quyết định ở mục 14.
