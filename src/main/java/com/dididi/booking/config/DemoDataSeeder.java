package com.dididi.booking.config;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.repository.CompanyRepository;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.CityGeo;
import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.domain.enums.Amenity;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.domain.enums.HotelTag;
import com.dididi.booking.hotel.domain.enums.PropertyType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seed DỮ LIỆU DEMO khối lượng lớn cho môi trường dev/demo.
 *
 * BẬT bằng cờ: app.seed.demo=true (mặc định false để không chạy mỗi lần khởi động).
 *   - VM/env:  -Dapp.seed.demo=true   hoặc   APP_SEED_DEMO=true
 *   - hoặc thêm vào application-local.yml:  app: { seed: { demo: true } }
 *
 * Tạo (idempotent, chỉ chạy 1 lần nhờ kiểm tra customer0001@dididi.local):
 *   CHẠY TRONG 1 TRANSACTION (all-or-nothing): nếu lỗi giữa chừng sẽ rollback toàn bộ,
 *   KHÔNG để lại dữ liệu dở -> chạy lại an toàn. Nên chạy trên DB booking sạch (chưa seed demo).
 *   - 5 admin           : admin1..admin5@dididi.local        / Admin@123
 *   - 50 vendor + 50 KS : vendor001..vendor050@dididi.local  / Vendor@123  (mỗi KS 3-4 hạng phòng, trống mặc định tới hết T8)
 *   - 400 khách lẻ      : customer0001..customer0400@dididi.local / Customer@123  (mỗi người 1 vé bay demo + 1 đơn KS)
 *   - 50 công ty + 50 nhân viên: corp001..corp050@dididi.local   / Corp@123    (mỗi người 1 vé bay + 1 đơn KS, công ty trả)
 *
 * Tồn kho phòng KHÔNG seed từng ngày: hệ thống mặc định available = RoomType.totalRooms khi
 * chưa có dòng inventory, nên mọi hạng phòng đều còn trống từ hôm nay đến hết tháng 8.
 */
@Component
@Profile("dev")
@Order(100)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo:false}")
    private boolean enabled;

    private final Random rnd = new Random(20260611L);
    private long codeSeq = 1;

    // Dữ liệu nền
    private static final String[] LAST = {"Nguyễn","Trần","Lê","Phạm","Hoàng","Huỳnh","Phan","Vũ","Võ","Đặng","Bùi","Đỗ","Hồ","Ngô","Dương","Lý"};
    private static final String[] FIRST = {"An","Bình","Châu","Dũng","Giang","Hà","Hải","Hoa","Hùng","Khoa","Lan","Linh","Minh","Nam","Nga","Ngọc","Phong","Quân","Sơn","Tâm","Thảo","Trang","Tuấn","Vân","Việt","Yến"};
    private static final String[] CITY = {"TP.HCM","Hà Nội","Đà Nẵng","Nha Trang","Huế","Phú Quốc","Đà Lạt","Hội An","Vũng Tàu","Hạ Long","Cần Thơ","Sa Pa"};
    private static final String[] HPRE = {"Saigon","Hanoi","Bay","Riverside","Sunrise","Golden","Royal","Ocean","Central","Grand","Lotus","Pearl","Emerald","Hoa Sen","Bình Minh"};
    private static final String[] HSUF = {"Hotel","Resort","Boutique Hotel","Inn","Suites","Hotel & Spa","Beach Resort","Residence"};
    private static final String[] STREETS = {"Trần Phú","Lê Lợi","Nguyễn Huệ","Hùng Vương","Bạch Đằng","Võ Nguyên Giáp","Hai Bà Trưng","Lý Thường Kiệt","Phan Chu Trinh","Nguyễn Thị Minh Khai","Trần Hưng Đạo","Lê Duẩn"};
    // room type template: name, capacity, base price, total rooms
    private static final Object[][] RTPL = {
        {"Standard", 2, 600000, 20},
        {"Superior", 2, 850000, 16},
        {"Deluxe",   3, 1200000, 12},
        {"Family",   4, 1600000, 8},
        {"Suite",    4, 2400000, 6},
    };
    // tuyến bay cục bộ: from, to, phút bay, giá gốc
    private static final Object[][] ROUTES = {
        {"HAN","SGN",135,1600000}, {"SGN","HAN",135,1600000},
        {"HAN","DAD",80,1000000},  {"DAD","HAN",80,1000000},
        {"SGN","DAD",85,1050000},  {"DAD","SGN",85,1050000},
        {"SGN","CXR",60,900000},   {"CXR","SGN",60,900000},
        {"HAN","CXR",110,1450000}, {"SGN","PQC",60,950000},
        {"HAN","HUI",75,1100000},  {"SGN","HUI",80,1150000},
        {"HAN","PQC",140,2050000}, {"DAD","CXR",70,950000},
    };

    public DemoDataSeeder(UserRepository userRepository, CompanyRepository companyRepository,
                          HotelRepository hotelRepository, RoomTypeRepository roomTypeRepository,
                          FlightRepository flightRepository, BookingRepository bookingRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (userRepository.existsByEmail("customer0001@dididi.local")) {
            log.info("[DemoDataSeeder] Đã seed trước đó (customer0001 tồn tại) -> bỏ qua.");
            return;
        }
        log.info("[DemoDataSeeder] Bắt đầu seed dữ liệu demo... (có thể mất vài giây)");

        // 6) 5 admin
        for (int i = 1; i <= 5; i++) {
            userRepository.save(newUser(String.format("admin%d@dididi.local", i), "Admin@123",
                    "Demo Admin " + i, Role.ADMIN, null));
        }

        // (Đã BỎ seed 200 chuyến bay cục bộ giả: catalog chuyến bay chỉ còn các chuyến ĐỒNG BỘ THẬT
        //  từ flight-provider. Vé bay demo bên dưới là bản ghi TỰ CHỨA, không tạo chuyến trong bảng flights.)

        // 3) 50 vendor + 50 khách sạn DIRECT + hạng phòng
        // Lưu catalog phòng để tạo đơn KS: [hotelId, roomTypeId, basePrice, "Tên KS - Tên phòng"]
        List<Object[]> roomCatalog = new ArrayList<>();
        for (int v = 1; v <= 50; v++) {
            User vendor = userRepository.save(newUser(String.format("vendor%03d@dididi.local", v), "Vendor@123",
                    "Vendor " + person(), Role.VENDOR, null));
            String city = CITY[rnd.nextInt(CITY.length)];
            String suffix = HSUF[rnd.nextInt(HSUF.length)];
            String hotelName = HPRE[rnd.nextInt(HPRE.length)] + " " + suffix + " " + v;
            int star = 3 + rnd.nextInt(3); // 3..5
            Hotel h = new Hotel();
            h.setName(hotelName);
            h.setCity(city);
            // dia chi tach nho (Nhom 1)
            String house = String.valueOf(10 + rnd.nextInt(280));
            String street = STREETS[rnd.nextInt(STREETS.length)];
            String ward = "Phường " + (1 + rnd.nextInt(15));
            String district = "Quận " + (1 + rnd.nextInt(12));
            h.setHouseNumber(house);
            h.setStreet(street);
            h.setWard(ward);
            h.setDistrict(district);
            h.setProvince(city);
            h.setAddress(HotelSupport.composeAddress(house, street, ward, district, city, city));
            // toa do tu trung tam thanh pho + jitter ~±2km (Nhom 2)
            CityGeo.Geo geo = CityGeo.lookup(city);
            if (geo != null) {
                h.setLat(round6(geo.lat() + (rnd.nextDouble() - 0.5) * 0.04));
                h.setLng(round6(geo.lng() + (rnd.nextDouble() - 0.5) * 0.04));
                h.setRegion(geo.region());
            }
            h.setDescription("Khách sạn do vendor tự quản trên Dididi tại " + city + ".");
            h.setStarRating(star);
            h.setActive(true);
            h.setSource(HotelSource.DIRECT);
            h.setVendorId(vendor.getId());
            h.setPropertyType(propertyTypeOf(suffix));
            h.setAmenities(randomAmenities(star));
            h.setTags(tagsFor(city, star, h.getAmenities()));
            hotelRepository.save(h);

            int numTypes = 3 + rnd.nextInt(2); // 3 hoặc 4 hạng phòng
            double mult = 0.9 + rnd.nextDouble() * 0.5; // hệ số giá theo KS
            for (int t = 0; t < numTypes; t++) {
                Object[] tpl = RTPL[t];
                String rtName = (String) tpl[0];
                int capacity = (int) tpl[1];
                long priceBase = ((Number) tpl[2]).longValue();
                int totalRooms = (int) tpl[3];
                long price = Math.round(priceBase * mult / 10000.0) * 10000L;
                RoomType rt = new RoomType();
                rt.setHotelId(h.getId());
                rt.setName(rtName);
                rt.setCapacity(capacity);
                rt.setBasePrice(BigDecimal.valueOf(price));
                rt.setCurrency("VND");
                rt.setTotalRooms(totalRooms);
                roomTypeRepository.save(rt);
                roomCatalog.add(new Object[]{h.getId(), rt.getId(), BigDecimal.valueOf(price), hotelName + " - " + rtName});
            }
        }
        log.info("[DemoDataSeeder] Đã tạo 5 admin, 50 vendor + 50 KS ({} hạng phòng), bắt đầu tạo khách + booking...", roomCatalog.size());

        List<Booking> batch = new ArrayList<>();

        // 4) 400 khách lẻ — mỗi người 1 vé bay + 1 đơn KS
        for (int c = 1; c <= 400; c++) {
            User u = userRepository.save(newUser(String.format("customer%04d@dididi.local", c), "Customer@123",
                    person(), Role.CUSTOMER, null));
            batch.add(flightBooking(u.getId(), null));
            batch.add(hotelBooking(u.getId(), roomCatalog.get(rnd.nextInt(roomCatalog.size())), null));
            if (batch.size() >= 200) { bookingRepository.saveAll(batch); batch.clear(); }
        }

        // 5) 50 công ty + 50 nhân viên — mỗi người 1 vé bay + 1 đơn KS (công ty trả)
        for (int k = 1; k <= 50; k++) {
            Company co = new Company();
            co.setName("Công ty Demo " + k);
            co.setCode(String.format("DEMOCO%03d", k));
            co.setBudgetTotal(new BigDecimal("50000000"));
            co.setBudgetUsed(BigDecimal.ZERO);
            co.setContactEmail(String.format("finance%03d@democo.local", k));
            co.setTaxCode(String.format("03%08d", k));
            co.setAddress((10 + rnd.nextInt(200)) + " Đường Doanh Nghiệp, " + CITY[rnd.nextInt(CITY.length)]);
            co.setApprovalThreshold(new BigDecimal("5000000"));
            co.setActive(true);
            companyRepository.save(co);

            User emp = userRepository.save(newUser(String.format("corp%03d@dididi.local", k), "Corp@123",
                    person(), Role.CUSTOMER, co.getId()));
            batch.add(flightBooking(emp.getId(), co.getId()));
            batch.add(hotelBooking(emp.getId(), roomCatalog.get(rnd.nextInt(roomCatalog.size())), co.getId()));
            if (batch.size() >= 200) { bookingRepository.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) { bookingRepository.saveAll(batch); batch.clear(); }

        log.info("[DemoDataSeeder] HOÀN TẤT. Users={}, Companies={}, Hotels={}, Flights={}, Bookings={}.",
                userRepository.count(), companyRepository.count(), hotelRepository.count(),
                flightRepository.count(), bookingRepository.count());
        log.info("[DemoDataSeeder] Mật khẩu demo: admin*/Admin@123, vendor*/Vendor@123, customer*/Customer@123, corp*/Corp@123");
    }

    // ----- helpers -----

    private String person() {
        return LAST[rnd.nextInt(LAST.length)] + " " + FIRST[rnd.nextInt(FIRST.length)] + " " + FIRST[rnd.nextInt(FIRST.length)];
    }

    private static double round6(double v) { return Math.round(v * 1_000_000d) / 1_000_000d; }

    private static PropertyType propertyTypeOf(String suffix) {
        String s = suffix.toLowerCase();
        if (s.contains("resort")) return PropertyType.RESORT;
        if (s.contains("residence") || s.contains("suites")) return PropertyType.APARTMENT;
        if (s.contains("inn")) return PropertyType.GUESTHOUSE;
        if (s.contains("boutique")) return PropertyType.HOMESTAY;
        return PropertyType.HOTEL;
    }

    private Set<Amenity> randomAmenities(int star) {
        Set<Amenity> s = new LinkedHashSet<>();
        s.add(Amenity.WIFI); s.add(Amenity.AC); s.add(Amenity.RECEPTION_24H); s.add(Amenity.PARKING);
        Amenity[] pool = Amenity.values();
        int extra = 4 + rnd.nextInt(6);
        for (int i = 0; i < extra; i++) s.add(pool[rnd.nextInt(pool.length)]);
        if (star >= 4) { s.add(Amenity.BREAKFAST); s.add(Amenity.POOL); }
        if (star >= 5) { s.add(Amenity.SPA); s.add(Amenity.GYM); s.add(Amenity.RESTAURANT); }
        return s;
    }

    private Set<HotelTag> tagsFor(String city, int star, Set<Amenity> ams) {
        Set<HotelTag> t = new LinkedHashSet<>();
        boolean beach = city.matches(".*(Nha Trang|Đà Nẵng|Phú Quốc|Vũng Tàu|Hạ Long|Hội An).*");
        if (beach) { t.add(HotelTag.SEA_VIEW); if (rnd.nextBoolean()) t.add(HotelTag.BEACHFRONT); }
        if (city.contains("HCM") || city.contains("Hà Nội")) t.add(HotelTag.CITY_CENTER);
        if (star >= 5) t.add(HotelTag.LUXURY); else if (star == 3) t.add(HotelTag.BUDGET);
        if (ams.contains(Amenity.FAMILY_ROOM) || rnd.nextInt(3) == 0) t.add(HotelTag.FAMILY_FRIENDLY);
        if (ams.contains(Amenity.AIRPORT_SHUTTLE)) t.add(HotelTag.NEAR_AIRPORT);
        if (ams.contains(Amenity.SPA)) t.add(HotelTag.ROMANTIC);
        return t;
    }

    private User newUser(String email, String pw, String fullName, Role role, Long companyId) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(pw));
        u.setFullName(fullName);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setCompanyId(companyId);
        return u;
    }

    private String nextCode() {
        return String.format("BK%010d", codeSeq++);
    }

    /** Vé bay demo TỰ CHỨA (không tạo chuyến trong bảng flights): hiển thị từ chính field của đơn. */
    private Booking flightBooking(Long userId, Long companyId) {
        Object[] r = ROUTES[rnd.nextInt(ROUTES.length)];
        String frm = (String) r[0], to = (String) r[1];
        long base = ((Number) r[3]).longValue();
        LocalDateTime dep = LocalDate.now().plusDays(1 + rnd.nextInt(70))
                .atTime(5 + rnd.nextInt(16), rnd.nextInt(12) * 5);
        long price = base + rnd.nextInt(6) * 60000L;
        Booking b = new Booking();
        b.setPublicCode(nextCode());
        b.setUserId(userId);
        b.setType(BookingType.FLIGHT);
        b.setTargetId(1L + rnd.nextInt(230));   // tham chiếu 1 chuyến provider (1..230); chỉ để liên kết, hiển thị dùng field đơn
        b.setTitle("VN" + (100 + rnd.nextInt(900)) + " " + frm + "→" + to);
        b.setTravelDate(dep);
        b.setQuantity(1);
        b.setAmount(BigDecimal.valueOf(price));
        b.setCurrency("VND");
        b.setProviderConfirmation("FP-" + (100000 + rnd.nextInt(900000)));
        b.setStatus(BookingStatus.CONFIRMED);
        b.setCompanyId(companyId);
        return b;
    }

    private Booking hotelBooking(Long userId, Object[] rt, Long companyId) {
        Long hotelId = (Long) rt[0];
        Long roomTypeId = (Long) rt[1];
        BigDecimal price = (BigDecimal) rt[2];
        String title = (String) rt[3];
        int nights = 1 + rnd.nextInt(4); // 1..4 đêm
        LocalDate checkIn = LocalDate.now().plusDays(1 + rnd.nextInt(70));
        LocalDate checkOut = checkIn.plusDays(nights);
        Booking b = new Booking();
        b.setPublicCode(nextCode());
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTargetId(hotelId);
        b.setRoomTypeId(roomTypeId);
        b.setTitle(title);
        b.setCheckIn(checkIn);
        b.setCheckOut(checkOut);
        b.setQuantity(1);
        b.setAmount(price.multiply(BigDecimal.valueOf(nights)));
        b.setCurrency("VND");
        b.setStatus(BookingStatus.CONFIRMED);
        b.setCompanyId(companyId);
        return b;
    }
}
