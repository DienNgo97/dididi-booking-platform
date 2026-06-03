# Dididi Booking Platform — main app

Main app (`booking-platform`, port 8080) of the Dididi booking platform — Phase 1 Foundation.
Spring Boot 3.5.14 · Java 17 · modular monolith. Base package `com.dididi.booking`.

Phase 1.5 sẽ thêm 2 repo mock riêng: `flight-provider` (8081), `hotel-pms` (8082).

## Prerequisites
- JDK 17 (đã có Maven wrapper `./mvnw`, không cần cài Maven riêng)
- MySQL 8 trên localhost:3306
- Redis trên localhost:6379

> Project **không dùng Lombok** → import vào Spring Tools for Eclipse là chạy, không cần cài thêm gì.

## Setup DB
```sql
CREATE DATABASE booking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'booking'@'localhost' IDENTIFIED BY 'booking_dev_pass';
GRANT ALL PRIVILEGES ON booking.* TO 'booking'@'localhost';
FLUSH PRIVILEGES;
```
Redis: `brew services start redis` → kiểm tra `redis-cli ping` → `PONG`.

## Run
- STS: chuột phải project → `Run As > Spring Boot App`
- Terminal: `./mvnw spring-boot:run`

App ở http://localhost:8080. Lần chạy dev đầu sẽ seed admin + 1 hotel demo (xem `config/DataInitializer`).
Tài khoản dev: `admin@dididi.local` / `Admin@123`

## Phase 1 — Definition of Done
| Check | Verify |
|---|---|
| App boot | `./mvnw spring-boot:run` không lỗi |
| Health UP | `curl localhost:8080/actuator/health` → `{"status":"UP"}` |
| Default login form | mở http://localhost:8080/login |
| Protected API → 401 | `curl -i localhost:8080/api/v1/ping` → 401 |
| Login cấp JWT | xem dưới |
| Protected API → 200 với token | xem dưới |
| MySQL connected | bảng `users` + `hotels` được tạo và seed |
| Redis connected | `redis-cli ping` → `PONG` |

### Lấy token và gọi endpoint bảo vệ
```bash
# 1) Login -> accessToken
curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@dididi.local","password":"Admin@123"}'

# 2) Gọi /api/v1/ping kèm token
TOKEN=...   # dán accessToken
curl -i localhost:8080/api/v1/ping -H "Authorization: Bearer $TOKEN"   # -> 200
```

## Đã thêm gì so với project gốc từ start.spring.io
- **pom.xml**: thêm `jjwt-api/impl/jackson` (0.12.6) + `micrometer-registry-prometheus`.
- **DididiBookingPlatformApplication**: thêm `@EnableJpaAuditing`, `@EnableAsync`.
- **resources**: thay `application.properties` bằng `application.yml` (+ `application-prod.yml`) + `i18n/*`.
- **com.dididi.booking**: thêm các package `config`, `common`, `identity`, `hotel` (xem dưới).

## Cấu trúc package
```
com.dididi.booking
├── DididiBookingPlatformApplication
├── config/        SecurityApiConfig (JWT chain), SecurityWebConfig (form/session + BCrypt12),
│                  WebMvcConfig (i18n), DataInitializer (seed dev)
├── common/        BaseEntity, ApiResponse/ErrorResponse, exceptions, GlobalExceptionHandler, PingApi
├── identity/      User, Role/UserStatus, JwtService, AuthService, JwtAuthenticationFilter, AuthApi
└── hotel/         Hotel entity + repository (demo Phase 1)
```
Module boundary: module gọi nhau **chỉ qua service interface**, không gọi repository trực tiếp.

## Tests
`./mvnw test` — `JwtServiceTest` là unit test thuần (không cần DB). `contextLoads()` dùng
Testcontainers (cần Docker) để khởi MySQL + Redis.

## Profiles
- `dev` (mặc định): `ddl-auto: update`, seed data, dev JWT secret.
- `prod`: `ddl-auto: validate`, secret/DB lấy từ ENV. Chạy với `--spring.profiles.active=prod`.

## Roadmap
Phase 1.5 (mock providers + integration) → Phase 2 (customer web Thymeleaf + Trip Planner) → Phase 3 (REST API).
