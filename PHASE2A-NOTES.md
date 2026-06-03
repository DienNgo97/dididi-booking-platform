# Dididi booking-platform — Phase 2a (Customer Web)

Bản project này = **Phase 1 + Integration (1.5) + Phase 2a**, gói đầy đủ. Thay nguyên thư mục
`dididi-booking-platform` của bạn bằng bản này (đã gồm sẵn integration module, khỏi ghép addon riêng).

> Bản này đã bao gồm tất cả fix trước đó (locale, security 401, `connectionTimeZone=LOCAL`).
> Nếu bạn có chỉnh tay gì khác ngoài các fix mình gợi ý, hãy sao lưu trước khi thay.

## Phase 2a có gì mới
1. **Lưu data sync xuống local**: `SyncJobOrchestrator` giờ ghi flights (bảng `flights`) + hotels
   (bảng `hotels`) vào DB `booking` mỗi lần sync (upsert theo `external_id`).
2. **Auth thật** (web): `CustomUserDetailsService` khớp bảng `users` + trang `/login`, `/register`
   Thymeleaf (thay form mặc định). Mật khẩu BCrypt.
3. **Trang khách**: `/` (home), `/hotels` + `/hotels/{id}`, `/flights` — đọc từ data đã sync. CSS riêng.

## Bước chạy
1. Cần MySQL + Redis chạy. **Bật cả flight-provider (8081) + hotel-pms (8082)** để có data sync.
2. ⚠️ **Schema**: bảng `hotels` được thêm cột (`external_id`, `min_price`, `currency`) + thêm bảng `flights`.
   `ddl-auto: update` thường tự thêm được. Nếu khởi động báo lỗi schema/unique trên `hotels`,
   vào DBeaver chạy (chỉ DEV): `DROP TABLE IF EXISTS flights; DROP TABLE IF EXISTS hotels;` rồi chạy lại
   để Hibernate tạo mới.
3. `./mvnw clean spring-boot:run` (booking-platform, 8080).
4. ~10s sau khi khởi động, sync chạy: log `Inventory sync finished: synced 20 flights, 5 hotels`,
   và bảng `flights`/`hotels` có data.

## Verify (mở trình duyệt)
- http://localhost:8080/ — trang chủ, có khách sạn nổi bật (sau khi sync).
- http://localhost:8080/register — tạo tài khoản (role CUSTOMER).
- http://localhost:8080/login — đăng nhập bằng tài khoản vừa tạo (hoặc admin@dididi.local / Admin@123).
- http://localhost:8080/hotels — danh sách khách sạn (lọc theo thành phố: `/hotels?city=Da Nang`).
- http://localhost:8080/flights?from=HAN&to=SGN — danh sách chuyến bay.
- Đăng nhập xong, góc phải hiện tên + nút Đăng xuất.

REST API cũ (`/api/auth/login`, `/api/v1/ping`) vẫn hoạt động qua JWT chain như trước.

## Chưa làm (để Phase 2b trở đi)
- Đặt phòng/đặt vé + thanh toán (booking + payment).
- Chi tiết khách sạn lấy loại phòng + giá theo ngày từ hotel-pms (hiện chỉ hiển thị info).
- Trip Planner (cần Google Maps API key của bạn).
- Self-service vendor dashboard, reviews, trang account.
- Phase 3 (REST API + Swagger cho mobile/SPA), Phase 4 (Angular), Phase 5 (Flutter), Phase 6 (observability/CI).

## Lưu ý
Mình **không build/chạy thử được** ở môi trường tạo code (không có mạng tải dependency).
Code viết bám sát Spring Boot 3.5 + những gì 3 service đang chạy, nhưng khi bạn về hãy chạy và
gửi log nếu có lỗi compile/khởi động — mình sửa ngay.
