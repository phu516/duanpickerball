# Dịch vụ nhận diện khuôn mặt

Cài `requirements.txt`. Đặt hai tệp mô hình chính thức của OpenCV Zoo `face_detection_yunet_2023mar.onnx` và `face_recognition_sface_2021dec.onnx` vào thư mục `models`, rồi chạy `uvicorn app:app --host 127.0.0.1 --port 8001` trong thư mục này. Khi chạy Vite, frontend gọi `/face-api` qua proxy mặc định; không cần đặt `VITE_FACE_API_URL`. Khi triển khai, chuyển tiếp `/face-api` tới dịch vụ này hoặc đặt `VITE_FACE_API_URL` thành URL công khai trước khi build. Camera trên thiết bị khác cần HTTPS.

Chạy một worker để các khung hình và mã xác minh dùng cùng bộ nhớ. Backend dùng FACE_SERVICE_URL (mặc định http://localhost:8001) để đổi mã quét dùng một lần. Trên Windows, trong thư mục face-service chạy: .\.venv\Scripts\python.exe -m uvicorn app:app --port 8001.
