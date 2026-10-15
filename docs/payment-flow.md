# Thanh toán trong PickleZone

## Chọn nhà cung cấp

Backend đọc `app.payment.provider` với giá trị `PAYOS` hoặc `SANDBOX`. Mặc định là
`SANDBOX`; trong profile `dev` có thể dùng endpoint xác nhận thử. Để thu tiền qua
payOS trên máy cá nhân, đặt các dòng sau trong `backend/application-local.properties`
(file này được `.gitignore` loại trừ):

```properties
app.payment.provider=PAYOS
app.payment.payos.client-id=<Client ID từ kênh payOS>
app.payment.payos.api-key=<API Key từ kênh payOS>
app.payment.payos.checksum-key=<Checksum Key từ kênh payOS>
```

Giữ khóa trong backend. Khi triển khai trên máy chủ, đặt ba biến môi trường
`APP_PAYMENT_PAYOS_CLIENT_ID`, `APP_PAYMENT_PAYOS_API_KEY` và
`APP_PAYMENT_PAYOS_CHECKSUM_KEY` trong nơi quản lý secret của máy chủ; đặt thêm
`PICKLE_PAYMENT_PROVIDER=PAYOS`. Đặt
`PICKLE_FRONTEND_ORIGIN` thành địa chỉ HTTPS của frontend để payOS đưa khách trở lại
đúng trang phiếu/đơn sau thanh toán.

Để thử giao dịch cọc giá trị nhỏ trên máy local, đặt
`app.booking.deposit-override-amount=2000` trong cùng file local. Chỉ phiếu mới
được tạo sau khi khởi động lại backend dùng mức cọc này; phiếu cũ giữ nguyên số
tiền đã lưu. Sau khi thử xong, xóa dòng ghi đè để quay lại mức cọc theo tỷ lệ
trong chính sách đặt sân.

## Luồng khách thanh toán

Khách tạo phiếu sân `HOLD`, sau đó bấm **Thanh toán cọc**. Backend tạo một khoản
`PENDING`, gọi payOS và lưu `orderCode`, mã link, QR, URL và hạn thanh toán. Tiền
cọc chỉ xác nhận phiếu khi payOS thông báo thành công và phiếu còn hiệu lực. Các
lần bấm lại trong hạn trả về cùng một QR. Khách thanh toán tiền sân còn lại trực
tiếp tại sân; admin có thể ghi nhận khoản thu thủ công trong hệ thống. Đơn hàng
dùng cùng cơ chế thanh toán trực tuyến. Trang chi tiết hỏi backend định kỳ để cập nhật trạng thái; khi khách quay
lại từ payOS, trang lấy giao dịch mới nhất bằng token của phiếu/đơn.

Hạn QR tiền cọc khớp với hạn giữ sân. Link của đơn có hạn 30 phút. Khi phiếu/đơn
bị hủy hoặc phiếu giữ sân hết hạn, backend yêu cầu payOS
đóng link còn chờ. Khoản tiền đến sau hạn hoặc sau khi hủy được ghi
`REVIEW_REQUIRED` để admin đối soát. Nếu cần hoàn, admin thực hiện chuyển khoản
hoàn bên ngoài hệ thống rồi ghi nhận chứng từ; ứng dụng chưa tự gọi API hoàn
tiền payOS. Giao dịch này không tự xác nhận sân.

## Webhook

Địa chỉ nhận webhook là:

```text
https://<địa chỉ backend công khai>/api/v1/webhooks/payos
```

Đăng ký URL này trong kênh thanh toán payOS sau khi backend có HTTPS công khai.
`localhost:8080` không thể nhận webhook từ payOS. Khi phát triển local, có thể
dùng URL HTTPS tạm thời chuyển tiếp về cổng backend. Mỗi lần URL tạm thay đổi phải
cập nhật lại trong payOS.

Backend xác minh chữ ký webhook bằng SDK payOS, đối chiếu mã link, mã khoản thu,
số tiền và tiền tệ, rồi ghi sự kiện duy nhất. Trang quay về (`returnUrl`) không
được dùng làm bằng chứng đã thu tiền. Nếu webhook bị trễ, API xem giao dịch sẽ
đối chiếu lại với payOS. Admin xem trạng thái, thời điểm và mã tham chiếu ở tab
**Thanh toán**.

payOS không có sandbox riêng. Kiểm thử ứng dụng trước bằng profile `test` với
khóa giả; lần kiểm tra cuối qua payOS là giao dịch tiền thật giá trị nhỏ. Không
dùng endpoint `/api/v1/dev/payments/{id}/complete` khi provider là `PAYOS`.
