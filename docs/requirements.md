# Yêu cầu đã đưa vào bản chạy local

- Một database chung. Nước uống là sản phẩm cùng SKU/tồn kho như vợt, áo quần.
- Khách không đăng nhập vẫn xem hàng, mua hàng, đặt sân. Một khách tạo được nhiều phiếu; một phiếu ứng với một sân và một khoảng giờ.
- Form sân yêu cầu họ tên và số điện thoại. Khoảng chơi tối thiểu 120 phút, theo bước 30 phút; HOLD 30 phút để trả cọc; sau giờ bắt đầu 30 phút admin có thể ghi NO_SHOW và giữ cọc.
- Admin thêm sân bất kỳ, đặt quy tắc giá, khóa lịch, hủy phiếu có lý do. Không xóa lịch sử booking.
- Giỏ hàng không giữ kho; tạo đơn giữ SKU. Đơn hủy/hết hạn trả lượng dự trữ. `COURT_DELIVERY` cần booking cùng token, đã check-in và đang trong giờ chơi.
- Admin và nhân viên dùng phiên đăng nhập Spring Security. Khách giữ token riêng cho từng tài nguyên; mã/số điện thoại không cấp quyền xem.

Các quyết định chưa chốt ở mục 14 của plan dùng giá trị thử: cọc 30%, mở cửa 06:00–23:00, giữ sân 30 phút, bước giờ 30 phút. Có thể đổi chính sách số qua `/api/v1/admin/settings` (giữ tối thiểu 120 phút). Kết nối cổng thanh toán, email/SMS và hosting cần cấu hình nhà cung cấp sau.
