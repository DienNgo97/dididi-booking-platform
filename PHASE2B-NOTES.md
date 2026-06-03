# Dididi booking-platform — Phase 2b (Booking + Payment stub + Trip Planner)

Bản này = Phase 1 + 1.5 + 2a + 3 + **2b**. Gói đầy đủ, thay nguyên thư mục như các lần trước.

## Phase 2b có gì mới

### Đặt vé / đặt phòng (web, có đăng nhập)
- **Chuyến bay**: `/flights` → nút **Đặt** → form hành khách → gọi flight-provider `book` → tạo đơn `PENDING_PAYMENT` → trang thanh toán.
- **Khách sạn**: `/hotels/{id}` giờ **hiển thị loại phòng** (lấy live từ hotel-pms) + form đặt (tên khách, ngày nhận/trả, số phòng) → gọi hotel-pms `reserve` → đơn `PENDING_PAYMENT` → thanh toán.
- **Thanh toán (giả lập)**: `/payment/{code}` → bấm "Thanh toán ngay" → đơn chuyển `CONFIRMED`, tạo bản ghi `payments` (status PAID, transactionRef). **Không tích hợp cổng thật.**
- **Đơn của tôi**: `/account/bookings` + chi tiết `/account/bookings/{code}` (có nút Huỷ).

### Trip Planner (bản rút gọn — không cần Google Maps)
- `/trip-planner` → nhập thành phố điểm đến (+ sân bay đi tuỳ chọn) → `/trip-planner/suggest`
  gợi ý **chuyến bay tới thành phố đó** (map city→airport: Ha Noi→HAN, TP.HCM→SGN, Da Nang→DAD, Hue→HUI, Nha Trang→CXR) + **khách sạn ở thành phố đó**, kèm nút đặt.
- (Bỏ phần gợi ý địa điểm tham quan vì cần Maps API key.)

### REST API (hoàn tất phần Phase 3 còn nợ)
- `POST /api/v1/bookings` (type=FLIGHT|HOTEL), `GET /api/v1/bookings/me`, `GET /api/v1/bookings/{code}`,
  `POST /api/v1/bookings/{code}/cancel`, `POST /api/v1/bookings/{code}/pay` — **cần Bearer token**.
- `POST /api/v1/trip-planner/suggest` (body `{ "city": "...", "from": "..." }`) — public.
- Tất cả hiện trong Swagger UI.

## Bảng mới (ddl-auto update tự tạo)
`bookings`, `payments`.

## Test (web) — cần bật cả 3 service + đã sync
1. Đăng nhập (tài khoản đã đăng ký ở Phase 2a, hoặc tạo mới).
2. **Đặt vé**: `/flights` → Đặt → điền form → Thanh toán giả lập → xem `/account/bookings`.
3. **Đặt phòng**: `/hotels/{id}` → chọn phòng, nhập ngày (VD nhận 2026-07-10, trả 2026-07-12) → Đặt phòng → Thanh toán → `/account/bookings`.
4. **Trip Planner**: `/trip-planner` → nhập "Da Nang" → thấy chuyến bay tới DAD + khách sạn ở Đà Nẵng.

## Test (API)
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@dididi.local","password":"Admin@123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# Đặt vé (flightId lấy từ GET /api/v1/flights)
curl -s -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"FLIGHT","flightId":1,"seats":2,"passengerName":"Test","contactEmail":"t@x.vn"}'

# Trip planner (public)
curl -s -X POST localhost:8080/api/v1/trip-planner/suggest \
  -H "Content-Type: application/json" -d '{"city":"Da Nang"}'
```

## Lưu ý
- Đặt vé/phòng gọi THẬT sang flight-provider/hotel-pms (giảm ghế/phòng tồn). Nếu provider tắt → báo lỗi "nhà cung cấp lỗi", đơn không tạo (Resilience4j retry + circuit breaker).
- Huỷ đơn hiện chỉ đổi trạng thái local (chưa gọi huỷ ngược sang provider) — có thể bổ sung sau.
- Mình không build/chạy thử được (máy không mạng). Chạy rồi gửi log nếu lỗi compile/khởi động — sửa ngay.

## Còn lại
Phase 4 (Angular admin SPA), Phase 5 (Flutter), Phase 6 (observability/CI). Review API + refresh token + rate limit cũng để dành.
