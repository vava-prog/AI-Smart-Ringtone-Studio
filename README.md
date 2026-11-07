# AI Smart Ringtone Studio — Android MVP

## Trạng thái
Đây là bộ mã nguồn MVP khởi đầu, chưa phải bản phát hành hoàn chỉnh.

### Đã có trong MVP
- Chọn tệp âm thanh từ thiết bị bằng Android Storage Access Framework.
- Nhập thời điểm bắt đầu/kết thúc.
- Cắt đoạn và xuất AAC trong container M4A bằng AndroidX Media3 Transformer.
- Lưu tệp tạm và chia sẻ kết quả.
- Xử lý cục bộ trên thiết bị.

### Chưa có
- Tìm kiếm/tải nhạc Internet.
- Kiểm tra giấy phép nguồn nhạc.
- AI tự động chọn đoạn nhạc.
- MP3 encoder / Opus encoder.
- So sánh ABX và đánh giá chất lượng nghe.
- Đặt trực tiếp làm nhạc chuông hệ thống.
- Kiểm thử trên thiết bị Xiaomi thực tế.

## Công nghệ
- Kotlin
- Jetpack Compose
- AndroidX Media3 Transformer 1.10.0
- Android SDK 36
- JDK 17
- Android Gradle Plugin 8.13.2 / Gradle 8.13

## Build bằng GitHub Actions từ điện thoại
1. Tạo repository GitHub mới.
2. Tải và giải nén ZIP này.
3. Đưa toàn bộ nội dung thư mục vào repository (Upload files).
4. Mở tab Actions.
5. Chạy workflow `Build Android APK` bằng `Run workflow`, hoặc push lên nhánh `main`.
6. Khi workflow hoàn tất, mở run mới nhất.
7. Trong mục Artifacts, tải `Smart-Ringtone-Studio-debug-apk`.
8. Giải nén và cài `app-debug.apk` trên Android.

## Build cục bộ
Cần JDK 17, Android SDK 36 và Gradle 8.13:
`gradle assembleDebug`

## Ghi chú kỹ thuật
Media3 Transformer dựa trên các codec mà Android MediaCodec hỗ trợ. Khả năng đọc/ghi phụ thuộc thiết bị và định dạng nguồn. APK debug không phải bản phát hành ký số để phân phối rộng rãi.
