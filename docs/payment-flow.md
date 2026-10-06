# Luồng thanh toán thử

Một khoản `payments` có trạng thái PENDING/SUCCEEDED/FAILED và thuộc phiếu hoặc đơn. Tiền cọc xác nhận phiếu chỉ sau khi khoản thu thành công và HOLD chưa hết hạn. Khoản hoàn nằm riêng trong `refunds`, không sửa payment thành FAILED. Admin tạo khoản thu tiền mặt/chuyển khoản đã xác minh qua `/api/v1/admin/payments/manual`; yêu cầu hoàn qua `/api/v1/admin/payments/{id}/refunds`. Hoàn thủ công cần tham chiếu rồi xác nhận qua `/api/v1/admin/refunds/{id}/confirm-manual`.

Profile `dev` có `SANDBOX` để thử luồng. Endpoint xác nhận thử yêu cầu token của đúng phiếu/đơn; không dùng số điện thoại. Chưa chọn nhà cung cấp nên không có webhook thật, chữ ký, URL thanh toán thật hay tự động chuyển khoản/hoàn tiền. Không dùng sandbox cho thu tiền thật.
