# Dididi booking-platform — Phase 3 (REST API + Swagger)

Bản này = Phase 1 + 1.5 + 2a + **3 (core)**. Gói đầy đủ, thay nguyên thư mục như lần trước.

## Phase 3 có gì mới
- **Swagger / OpenAPI**: `springdoc-openapi`. Mở UI: **http://localhost:8080/swagger-ui.html**
  (có nút **Authorize** để dán Bearer token và "Try it out").
- **Auth API**: thêm `POST /api/auth/register`, `GET /api/auth/me` (ngoài `POST /api/auth/login` cũ).
- **Public API** (không cần token):
  - `GET /api/v1/hotels?city=&page=&size=` , `GET /api/v1/hotels/{id}`
  - `GET /api/v1/flights?from=&to=&date=` , `GET /api/v1/flights/{id}`
  - `GET /api/v1/master/cities` , `GET /api/v1/master/airports`
- **Admin API** (cần JWT role ADMIN/SUPER_ADMIN/VENDOR):
  - `GET/POST /api/admin/v1/hotels` , `PUT/DELETE /api/admin/v1/hotels/{id}`
- **CORS** mở cho `http://localhost:4200` (Angular dev) trên `/api/**`.

> `/api/v1/ping` vẫn cần token (giữ DoD Phase 1). Chỉ hotels/flights/master GET là public.

## Test nhanh
```bash
# Public (không token)
curl -s "localhost:8080/api/v1/hotels?city=Da Nang"
curl -s "localhost:8080/api/v1/flights?from=HAN&to=SGN&date=2026-07-01"
curl -s localhost:8080/api/v1/master/cities

# Đăng ký + đăng nhập
curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"u1@dididi.local","password":"secret6","fullName":"User 1"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@dididi.local","password":"Admin@123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -s localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# Admin (cần token admin)
curl -s localhost:8080/api/admin/v1/hotels -H "Authorization: Bearer $TOKEN"
```
Hoặc dùng thẳng **Swagger UI**: Authorize → dán token → Try it out.

## Lưu ý version
`springdoc-openapi-starter-webmvc-ui` đang để **2.8.6**. Nếu khi khởi động báo lỗi liên quan springdoc
với Spring Boot 3.5, nâng version lên bản 2.8.x mới nhất (springdoc bám sát theo Boot).

## Chưa làm (Phase sau)
- Booking/Payment/Review API + Trip Planner API (cần Phase 2b dựng các module booking/payment/review/trip).
- refresh token / logout (blacklist Redis) / forgot-password.
- Rate limit (bucket4j) — DoD liệt kê nhưng để sau, không ảnh hưởng API chạy.
- MapStruct (hiện map thủ công trong DTO `from(...)`).

## Lưu ý chung
Mình không build/chạy thử được (không có mạng). Chạy rồi gửi log nếu lỗi compile/khởi động — sửa ngay.
