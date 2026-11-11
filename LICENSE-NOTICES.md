# Giấy phép và thông báo của bên thứ ba / Third-party notices

Mã nguồn gốc của dự án này theo giấy phép **MIT** (xem `LICENSE`). Các thành phần bên dưới giữ nguyên
giấy phép riêng của chúng.

## Thư viện đi kèm trong ứng dụng

| Thành phần | Giấy phép | Ghi chú |
|---|---|---|
| AndroidX (Core, Activity, Lifecycle), Jetpack Compose (Material 3) | Apache License 2.0 | Phụ thuộc Gradle |
| AndroidX Media3 (ExoPlayer, Transformer, Common) | Apache License 2.0 | Phát nhạc và xuất M4A (AAC) |
| Kotlin, kotlinx.coroutines | Apache License 2.0 | |
| **LAME (bản Java "java-lame" 3.98.4)** | **GNU LGPL** | Mã nguồn nằm trong `app/src/main/java/net/sourceforge/lame/`; văn bản giấy phép và nguồn gốc ở `third_party/lame/` |

### Về LAME (LGPL)

Xuất MP3 dùng bản Java của bộ mã hóa LAME, giấy phép GNU LGPL. Mã nguồn được đưa trực tiếp vào repo
và bạn có thể sửa đổi, build lại theo điều khoản LGPL. Nếu bạn phát hành bản dựng của ứng dụng này, hãy
giữ nguyên thông báo bản quyền và giấy phép LGPL, cung cấp mã nguồn của phần LAME (đã có trong repo)
và cho phép người dùng thay bằng phiên bản LAME đã sửa đổi.

### Mã tự viết trong dự án (theo giấy phép MIT)

Bộ mã hóa FLAC (`FlacEncoder.java`), bộ đổi tần số lấy mẫu (`Resampler.java`), bộ xử lý EQ/giới hạn đỉnh,
bộ chọn đoạn thông minh và trợ lý lệnh được viết riêng cho dự án này. Không có mã FFmpeg hay FLAC/libFLAC
nào được đưa vào.

## Thư viện chỉ dùng khi kiểm thử

- JUnit 4 (Eclipse Public License 1.0)
- org.json (giấy phép JSON, chỉ dùng trong unit test)

## Dịch vụ và nội dung trực tuyến

Tab Khám phá gọi các dịch vụ bên ngoài. Ứng dụng này **không liên kết hay được bảo trợ** bởi họ, và việc
dùng dịch vụ phải tuân theo điều khoản của từng dịch vụ:

- **Openverse** (api.openverse.org), **Internet Archive** (archive.org), **Jamendo** (api.jamendo.com): mỗi bài nhạc
  có giấy phép riêng (thường là Creative Commons). Ứng dụng chỉ hiện nhạc cho phép chỉnh sửa (loại giấy phép
  ND), nhưng giấy phép do người đăng tải khai báo có thể sai, và giấy phép NC (phi thương mại) cấm dùng
  thương mại. Hãy kiểm tra giấy phép trên trang gốc và ghi công tác giả theo yêu cầu. Link trang gốc được
  lưu cùng nhạc chuông trong Lịch sử. Jamendo cần `client_id` do chính bạn đăng ký và có điều khoản API riêng.
- **Deezer** (api.deezer.com): chỉ lấy tên bài và nghệ sĩ từ bảng xếp hạng để tham khảo xu hướng, không lấy âm
  thanh. Điều khoản API của Deezer vẫn áp dụng.
- Các liên kết đến Pixabay, Jamendo, Free Music Archive chỉ để tiện tham khảo.
- Ứng dụng **không** tải nhạc từ YouTube hay TikTok. Ô nhập link chỉ dùng để mở lại video và ghi nguồn.
- Tên "YouTube", "TikTok" và các thương hiệu khác thuộc về chủ sở hữu tương ứng.

## Lưu ý khi phát hành

Hãy xem lại giấy phép và điều khoản của tất cả thành phần nói trên trước khi phân phối bản dựng công khai
(ví dụ trên Google Play).
