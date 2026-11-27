# Đăng nhập trên Render

Khi frontend là Render Static Site và backend là Web Service ở hai origin khác nhau, trình duyệt có thể không gửi lại cookie `XSRF-TOKEN`/`JSESSIONID`. Khi đó `GET /api/v1/auth/csrf` trả 200 nhưng `POST /api/v1/auth/login` trả `403 CSRF_INVALID`. Backend tự dùng cookie `SameSite=None; Secure` khi Render đặt `RENDER=true`; vẫn có trình duyệt chặn cookie bên thứ ba.

## Cấu hình khuyến nghị: cùng origin

1. Trong Render Static Site, mở **Redirects/Rewrites**. Thêm rule **Rewrite**: Source `/api/*`, Destination `https://TEN-BACKEND.onrender.com/api/*`. Thay bằng URL backend thật; đặt rule này trước rule SPA `/*` → `/index.html` nếu có.
2. Xóa `VITE_API_URL` khỏi Environment của frontend. **Rebuild/deploy** frontend vì Vite nhúng biến này lúc build. Sau đó trình duyệt gọi `/api/v1` trên chính origin frontend.
3. Trên backend, đặt `PICKLE_FRONTEND_ORIGIN` bằng origin frontend thật, ví dụ `https://TEN-FRONTEND.onrender.com` (không có dấu `/` cuối). Đặt `PICKLE_COOKIE_SAME_SITE=lax`; giữ `PICKLE_COOKIE_SECURE=true` hoặc bỏ biến này để backend dùng mặc định Render.
4. Deploy lại backend. Trong DevTools Network, kiểm tra `GET /api/v1/auth/csrf` nhận `XSRF-TOKEN`, `POST /api/v1/auth/login` gửi lại cookie đó và response đăng nhập nhận `JSESSIONID`.

Nếu vẫn dùng `VITE_API_URL` trỏ thẳng tới backend khác origin, để backend dùng mặc định Render (`SameSite=None; Secure`) hoặc đặt rõ `PICKLE_COOKIE_SAME_SITE=none` và `PICKLE_COOKIE_SECURE=true`. Cách này có thể thất bại khi trình duyệt chặn cookie bên thứ ba.

Render hỗ trợ rewrite tới URL công khai và chuyển tiếp method, body, cookie: [Redirects and Rewrites](https://render.com/docs/redirects-rewrites), [ví dụ proxy trên Render](https://render.com/articles/how-to-proxy-analytics-through-your-own-domain).
