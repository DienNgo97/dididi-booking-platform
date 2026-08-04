package com.dididi.booking.config;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.VnLocations;
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
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import com.dididi.booking.review.repository.ReviewRepository;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.Reaction;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.FollowStatus;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReactionType;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.FollowRepository;
import com.dididi.booking.social.repository.PostRepository;
import com.dididi.booking.social.repository.ReactionRepository;
import com.dididi.booking.social.repository.SocialProfileRepository;
import com.dididi.booking.social.service.HashtagService;
import com.dididi.booking.social.service.SocialProfileService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * NHÂN 5 DỮ LIỆU DEMO (Jay yêu cầu 3/8/2026): đo số lượng hiện có của từng hạng mục rồi tạo thêm 4 lần
 * để tổng đạt ~5×: khách sạn + hạng phòng, chuyến bay (cục bộ), người dùng CUSTOMER, đơn đặt + thanh toán,
 * đánh giá, cộng đồng (bài viết + bình luận + like + follow, đồng bộ counters + hashtag).
 *
 * BẬT bằng cờ: app.seed.scale5x=true (application-local.yml). CHẠY MỘT LẦN rồi tắt cờ.
 * IDEMPOTENT: marker = user "x5user0001@dididi.local"; đã có -> bỏ qua toàn bộ (chạy lại an toàn).
 * Mọi bản ghi đều nhận diện được: user email x5user*, booking publicCode X5*, hotel externalId 810001+,
 * flight externalId 910001+ (dải cục bộ >= 900000 - đặt được, trừ ghế thật).
 *
 * createdAt bị JPA Auditing ghi đè lúc persist -> cuối phiên dùng native UPDATE để RẢI ngày tạo
 * (booking/payment theo ngày đi, review sau check-out, post/comment rải ~4 tháng) cho dashboard đẹp.
 *
 * LƯU Ý: KS seed không có vendorId (giống 300 KS đợt trước) -> không vào báo cáo hoa hồng vendor (M6).
 * Meilisearch tự re-index ở ApplicationReadyEvent (chạy SAU CommandLineRunner) nên index có ngay KS mới.
 */
@Component
@Profile("dev")
@Order(400)
public class DataScale5xSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataScale5xSeeder.class);

    public static final long HOTEL_EXT_BASE = 810_000L;   // KS x5: 810001+
    public static final long FLIGHT_EXT_BASE = 910_000L;  // chuyến bay cục bộ x5: 910001+ (>=900000 = local)
    private static final String MARKER_EMAIL = "x5user0001@dididi.local";

    @Value("${app.seed.scale5x:false}")
    private boolean enabled;

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final FollowRepository followRepository;
    private final SocialProfileRepository profileRepository;
    private final SocialProfileService profileService;
    private final HashtagService hashtagService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    private final Random rnd = new Random(20260803L);
    private long codeSeq = 1;

    public DataScale5xSeeder(UserRepository userRepository, HotelRepository hotelRepository,
                             RoomTypeRepository roomTypeRepository, FlightRepository flightRepository,
                             BookingRepository bookingRepository, PaymentRepository paymentRepository,
                             ReviewRepository reviewRepository, PostRepository postRepository,
                             CommentRepository commentRepository, ReactionRepository reactionRepository,
                             FollowRepository followRepository, SocialProfileRepository profileRepository,
                             SocialProfileService profileService, HashtagService hashtagService,
                             PasswordEncoder passwordEncoder, EntityManager em) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.followRepository = followRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.hashtagService = hashtagService;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        if (userRepository.existsByEmail(MARKER_EMAIL)) {
            log.info("[x5] Đã seed x5 trước đó -> bỏ qua. (Tắt cờ app.seed.scale5x để khỏi thấy log này.)");
            return;
        }

        // ----- 0) Đo baseline hiện có -----
        int h0 = hotelRepository.findByActiveTrue().size();
        long f0 = flightRepository.count();
        long u0 = userRepository.findByRole(Role.CUSTOMER, PageRequest.of(0, 1)).getTotalElements();
        long b0 = bookingRepository.count();
        long r0 = reviewRepository.count();
        long p0 = postRepository.count();
        long c0 = commentRepository.count();
        long re0 = reactionRepository.count();
        long fo0 = followRepository.count();
        log.info("[x5] Baseline: hotels={} flights={} customers={} bookings={} reviews={} posts={} comments={} reactions={} follows={}",
                h0, f0, u0, b0, r0, p0, c0, re0, fo0);

        int addHotels = h0 * 4;
        int addFlights = (int) f0 * 4;
        int addUsers = Math.max(40, (int) u0 * 4);
        int addReviews = (int) r0 * 4;
        int addBookings = Math.max((int) b0 * 4, addReviews + 200); // đủ đơn quá khứ để gắn review
        int addPosts = Math.max(40, (int) p0 * 4);
        int addComments = Math.max(80, (int) c0 * 4);
        int addReactions = Math.max(150, (int) re0 * 4);
        int addFollows = Math.max(80, (int) fo0 * 4);

        List<Hotel> newHotels = seedHotels(addHotels);
        List<Flight> newFlights = seedFlights(addFlights);
        List<User> newUsers = seedUsers(addUsers);
        List<Hotel> allHotels = hotelRepository.findByActiveTrue();
        Map<Long, List<RoomType>> roomsByHotel = loadRooms(allHotels);
        List<Booking> reviewables = seedBookingsAndPayments(addBookings, newUsers, allHotels, roomsByHotel, newFlights);
        Map<Long, String> nameByUserId = new HashMap<>();
        for (User u : newUsers) nameByUserId.put(u.getId(), u.getFullName());
        seedReviews(addReviews, reviewables, nameByUserId);
        seedSocial(addPosts, addComments, addReactions, addFollows, newUsers, allHotels);
        spreadDates();

        log.info("[x5] HOÀN TẤT nhân 5: +{} KS, +{} chuyến bay, +{} user, +{} đơn (kèm thanh toán), +{} đánh giá, +{} bài viết, +{} bình luận, +{} like, +{} follow. Nhớ tắt cờ app.seed.scale5x.",
                newHotels.size(), newFlights.size(), newUsers.size(), addBookings, addReviews, addPosts, addComments, addReactions, addFollows);
    }

    // ==================== 1) KHÁCH SẠN + HẠNG PHÒNG ====================

    private static final String[] PRE = {"Saigon", "Hanoi", "Bay", "Riverside", "Sunrise", "Golden",
            "Royal", "Ocean", "Central", "Grand", "Lotus", "Pearl", "Emerald", "Hoa Sen", "Bình Minh",
            "Mường Thanh", "An Phú", "Đông Dương", "Sao Mai", "Hải Âu", "Kim Liên", "Phương Nam",
            "Thiên Thanh", "Ngọc Lan", "Hồng Hà", "Trường Sơn", "Bạch Dương", "Minh Châu"};
    private static final String[] SUF = {"Hotel", "Resort", "Boutique Hotel", "Inn", "Suites",
            "Hotel & Spa", "Beach Resort", "Residence", "Homestay", "Villa", "Grand Hotel", "Lodge"};
    private static final Object[][] RTPL = {
            {"Standard", 2, 600000, 20, 22},
            {"Superior", 2, 850000, 16, 26},
            {"Deluxe", 3, 1200000, 12, 32},
            {"Family", 4, 1600000, 8, 40},
            {"Suite", 4, 2400000, 6, 55},
    };

    private List<Hotel> seedHotels(int n) {
        // Trải theo trọng số 12 TP của VnLocations
        List<VnLocations.Loc> weighted = new ArrayList<>();
        for (VnLocations.Loc loc : VnLocations.ALL) {
            for (int i = 0; i < loc.weight; i++) weighted.add(loc);
        }
        Set<String> used = new HashSet<>();
        List<Hotel> out = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            VnLocations.Loc loc = weighted.get(rnd.nextInt(weighted.size()));
            int star = 3 + rnd.nextInt(3);
            double mult = 0.85 + rnd.nextDouble() * 0.55;
            mult *= (1.0 + (star - 3) * 0.28);

            String suffix = SUF[rnd.nextInt(SUF.length)];
            String house = String.valueOf(1 + rnd.nextInt(400));
            String street = loc.streets[rnd.nextInt(loc.streets.length)];
            String ward = loc.wards[rnd.nextInt(loc.wards.length)];

            Hotel h = new Hotel();
            h.setExternalId(HOTEL_EXT_BASE + i);
            h.setName(uniqueName(loc.city, suffix, used));
            h.setCity(loc.city);
            h.setHouseNumber(house);
            h.setStreet(street);
            h.setWard(ward);
            h.setDistrict(null);
            h.setProvince(loc.province);
            h.setAddress(HotelSupport.composeAddress(house, street, ward, null, loc.province, loc.city));
            h.setLat(round6(loc.lat + (rnd.nextDouble() - 0.5) * 0.05));
            h.setLng(round6(loc.lng + (rnd.nextDouble() - 0.5) * 0.05));
            h.setRegion(loc.region);
            h.setStarRating(star);
            h.setDescription("Khách sạn " + star + " sao tại " + loc.city + ", " + loc.province
                    + ". Vị trí thuận tiện, gần điểm tham quan và ẩm thực địa phương.");
            h.setActive(true);
            h.setSource(HotelSource.DIRECT); // phòng seed nội bộ -> đặt được (bài học 300 KS)
            h.setCurrency("VND");
            h.setPropertyType(propertyTypeOf(suffix));
            Set<Amenity> ams = randomAmenities(star);
            h.setAmenities(ams);
            h.setTags(tagsFor(loc, star, ams));
            h.setMinPrice(BigDecimal.valueOf(round10k(((Number) RTPL[0][2]).longValue() * mult)));
            hotelRepository.save(h);
            out.add(h);

            int numTypes = 3 + rnd.nextInt(2);
            for (int t = 0; t < numTypes; t++) {
                Object[] tpl = RTPL[t];
                RoomType rt = new RoomType();
                rt.setHotelId(h.getId());
                rt.setName((String) tpl[0]);
                rt.setCapacity((int) tpl[1]);
                rt.setBasePrice(BigDecimal.valueOf(round10k(((Number) tpl[2]).longValue() * mult)));
                rt.setCurrency("VND");
                rt.setTotalRooms((int) tpl[3]);
                rt.setAreaSqm((int) tpl[4]);
                roomTypeRepository.save(rt);
            }
        }
        log.info("[x5] +{} khách sạn (externalId {}+)", out.size(), HOTEL_EXT_BASE + 1);
        return out;
    }

    // ==================== 2) CHUYẾN BAY (cục bộ, đặt được) ====================

    private static final String[] AIRPORTS = {"SGN", "HAN", "DAD", "CXR", "HUI", "PQC"};
    private static final String[] AIRLINES = {"VN", "VJ", "QH", "BL"};
    private static final String[] AIRCRAFT = {"A321", "A320", "A350", "B787"};

    private List<Flight> seedFlights(int n) {
        List<Flight> out = new ArrayList<>(n);
        List<Flight> batch = new ArrayList<>(200);
        for (int i = 1; i <= n; i++) {
            String from = AIRPORTS[rnd.nextInt(AIRPORTS.length)];
            String to;
            do { to = AIRPORTS[rnd.nextInt(AIRPORTS.length)]; } while (to.equals(from));
            String airline = AIRLINES[rnd.nextInt(AIRLINES.length)];
            LocalDateTime dep = LocalDate.now().plusDays(1 + rnd.nextInt(45))
                    .atTime(5 + rnd.nextInt(17), (rnd.nextInt(12)) * 5);

            Flight f = new Flight();
            f.setExternalId(FLIGHT_EXT_BASE + i);
            f.setFlightNumber(airline + (100 + rnd.nextInt(900)));
            f.setAirlineCode(airline);
            f.setFromAirport(from);
            f.setToAirport(to);
            f.setDepartureTime(dep);
            f.setArrivalTime(dep.plusMinutes(75 + rnd.nextInt(60)));
            f.setPrice(BigDecimal.valueOf(round10k(750_000 + rnd.nextInt(1_800_000))));
            f.setCurrency("VND");
            f.setAvailableSeats(120 + rnd.nextInt(101));
            f.setAircraftType(AIRCRAFT[rnd.nextInt(AIRCRAFT.length)]);
            batch.add(f);
            out.add(f);
            if (batch.size() >= 200) { flightRepository.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) flightRepository.saveAll(batch);
        log.info("[x5] +{} chuyến bay cục bộ (externalId {}+)", out.size(), FLIGHT_EXT_BASE + 1);
        return out;
    }

    // ==================== 3) NGƯỜI DÙNG ====================

    private static final String[] HO = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan",
            "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương"};
    private static final String[] DEM = {"Văn", "Thị", "Hữu", "Đức", "Ngọc", "Thanh", "Quốc", "Xuân", "Thu", "Gia"};
    private static final String[] TEN = {"An", "Bình", "Chi", "Dũng", "Giang", "Hà", "Hải", "Hạnh",
            "Hiếu", "Hùng", "Huy", "Khánh", "Lan", "Linh", "Long", "Mai", "Minh", "Nam", "Ngọc",
            "Nhung", "Phong", "Phúc", "Quân", "Quang", "Quỳnh", "Sơn", "Thảo", "Thành", "Trang",
            "Trung", "Tuấn", "Tú", "Vy", "Yến"};

    private List<User> seedUsers(int n) {
        String sharedHash = passwordEncoder.encode("Customer@123"); // encode 1 lần - BCrypt chậm, n lớn
        List<User> out = new ArrayList<>(n);
        List<User> batch = new ArrayList<>(200);
        for (int i = 1; i <= n; i++) {
            User u = new User();
            u.setEmail(String.format("x5user%04d@dididi.local", i));
            u.setPasswordHash(sharedHash);
            u.setFullName(HO[rnd.nextInt(HO.length)] + " " + DEM[rnd.nextInt(DEM.length)] + " " + TEN[rnd.nextInt(TEN.length)]);
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);
            batch.add(u);
            out.add(u);
            if (batch.size() >= 200) { userRepository.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) userRepository.saveAll(batch);
        log.info("[x5] +{} customer (x5user*@dididi.local / Customer@123)", out.size());
        return out;
    }

    // ==================== 4) ĐƠN ĐẶT + THANH TOÁN ====================

    private Map<Long, List<RoomType>> loadRooms(List<Hotel> hotels) {
        Set<Long> hotelIds = new HashSet<>();
        for (Hotel h : hotels) hotelIds.add(h.getId());
        Map<Long, List<RoomType>> map = new HashMap<>();
        for (RoomType rt : roomTypeRepository.findAll()) { // 1 query thay vì 1 query/KS
            if (hotelIds.contains(rt.getHotelId())) {
                map.computeIfAbsent(rt.getHotelId(), k -> new ArrayList<>()).add(rt);
            }
        }
        return map;
    }

    private String nextCode() { return String.format("X5%010d", codeSeq++); }

    /** @return danh sách đơn HOTEL đã CONFIRMED và check-out trong quá khứ (ứng viên gắn review). */
    private List<Booking> seedBookingsAndPayments(int n, List<User> users, List<Hotel> hotels,
                                                  Map<Long, List<RoomType>> roomsByHotel, List<Flight> flights) {
        List<Long> hotelIdsWithRooms = new ArrayList<>(roomsByHotel.keySet());
        Map<Long, Hotel> hotelById = new HashMap<>();
        for (Hotel h : hotels) hotelById.put(h.getId(), h);
        List<Booking> reviewables = new ArrayList<>();
        List<Booking> batch = new ArrayList<>(200);
        List<Booking> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            User u = users.get(rnd.nextInt(users.size()));
            boolean isHotel = flights.isEmpty() || rnd.nextInt(10) < 7; // 70% hotel (100% nếu chưa có chuyến bay)
            Booking b = new Booking();
            b.setPublicCode(nextCode());
            b.setUserId(u.getId());
            b.setQuantity(1);
            b.setCurrency("VND");

            int offset = rnd.nextInt(360) - 300; // -300..+59 ngày (thiên về quá khứ cho báo cáo)
            if (isHotel) {
                Long hid = hotelIdsWithRooms.get(rnd.nextInt(hotelIdsWithRooms.size()));
                List<RoomType> rts = roomsByHotel.get(hid);
                RoomType rt = rts.get(rnd.nextInt(rts.size()));
                Hotel h = hotelById.get(hid);
                int nights = 1 + rnd.nextInt(4);
                LocalDate in = LocalDate.now().plusDays(offset);
                b.setType(BookingType.HOTEL);
                b.setTargetId(hid);
                b.setRoomTypeId(rt.getId());
                b.setTitle((h != null ? h.getName() : "Khách sạn") + " - " + rt.getName());
                b.setCheckIn(in);
                b.setCheckOut(in.plusDays(nights));
                b.setAmount(rt.getBasePrice().multiply(BigDecimal.valueOf(nights)));
            } else {
                Flight f = flights.get(rnd.nextInt(flights.size()));
                b.setType(BookingType.FLIGHT);
                b.setTargetId(f.getId());
                b.setTitle(f.getFlightNumber() + " " + f.getFromAirport() + "→" + f.getToAirport());
                b.setTravelDate(offset >= 0 ? f.getDepartureTime()
                        : LocalDate.now().plusDays(offset).atTime(6 + rnd.nextInt(15), 0));
                b.setAmount(f.getPrice());
                b.setProviderConfirmation("X5F-" + (100000 + rnd.nextInt(900000)));
            }

            // Trạng thái: 75% CONFIRMED, 13% CANCELLED, 7% PENDING_PAYMENT, 5% FAILED
            int roll = rnd.nextInt(100);
            BookingStatus st = roll < 75 ? BookingStatus.CONFIRMED
                    : roll < 88 ? BookingStatus.CANCELLED
                    : roll < 95 ? BookingStatus.PENDING_PAYMENT
                    : BookingStatus.FAILED;
            b.setStatus(st);
            batch.add(b);
            all.add(b);
            if (isHotel && st == BookingStatus.CONFIRMED && b.getCheckOut().isBefore(LocalDate.now())) {
                reviewables.add(b);
            }
            if (batch.size() >= 200) { bookingRepository.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) bookingRepository.saveAll(batch);

        // Thanh toán tương ứng
        List<Payment> pays = new ArrayList<>(200);
        int paid = 0;
        for (Booking b : all) {
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) continue;
            Payment p = new Payment();
            p.setBookingId(b.getId());
            p.setAmount(b.getAmount());
            p.setCurrency("VND");
            p.setMethod(rnd.nextBoolean() ? "VNPAY" : "MOCK");
            p.setStatus(b.getStatus() == BookingStatus.CONFIRMED ? PaymentStatus.PAID
                    : b.getStatus() == BookingStatus.CANCELLED
                        ? (rnd.nextBoolean() ? PaymentStatus.REFUNDED : PaymentStatus.PAID)
                        : PaymentStatus.FAILED);
            p.setTransactionRef("X5TX-" + b.getPublicCode().substring(2));
            pays.add(p);
            paid++;
            if (pays.size() >= 200) { paymentRepository.saveAll(pays); pays.clear(); }
        }
        if (!pays.isEmpty()) paymentRepository.saveAll(pays);
        log.info("[x5] +{} đơn đặt (+{} thanh toán); {} đơn đủ điều kiện gắn review", all.size(), paid, reviewables.size());
        return reviewables;
    }

    // ==================== 5) ĐÁNH GIÁ ====================

    private static final String[] REVIEW_GOOD = {
            "Phòng sạch, nhân viên thân thiện, sẽ quay lại.",
            "Vị trí quá tiện, đi bộ ra điểm tham quan chính chỉ vài phút.",
            "Bữa sáng ngon, nhiều món địa phương. Rất đáng tiền.",
            "View đẹp, phòng thơm, check-in nhanh gọn.",
            "Giường êm, cách âm tốt, ngủ ngon cả đêm.",
            "Hồ bơi sạch, khu vực chung được chăm chút. Gia đình mình rất thích.",
            "Nhân viên hỗ trợ thuê xe máy và chỉ đường nhiệt tình.",
            "Giá hợp lý so với chất lượng, phòng rộng hơn mong đợi."};
    private static final String[] REVIEW_MID = {
            "Ổn trong tầm giá, nhưng cách âm hơi kém.",
            "Phòng hơi nhỏ so với ảnh, được cái sạch sẽ.",
            "Vị trí tốt nhưng bữa sáng ít món.",
            "Check-in hơi chậm lúc đông khách, còn lại ổn.",
            "Wifi lúc nhanh lúc chậm, tiện nghi cơ bản đủ dùng."};
    private static final String[] REVIEW_BAD = {
            "Điều hoà kêu to, báo lễ tân xử lý hơi lâu.",
            "Phòng cũ hơn ảnh, cần bảo trì lại.",
            "Đặt phòng view biển nhưng nhận phòng view tường, hơi thất vọng."};

    private void seedReviews(int n, List<Booking> reviewables, Map<Long, String> nameByUserId) {
        int made = 0;
        List<Review> batch = new ArrayList<>(200);
        for (Booking b : reviewables) {
            if (made >= n) break;
            int roll = rnd.nextInt(100); // 48% 5*, 30% 4*, 14% 3*, 6% 2*, 2% 1*
            int rating = roll < 48 ? 5 : roll < 78 ? 4 : roll < 92 ? 3 : roll < 98 ? 2 : 1;
            String cmt = rating >= 4 ? REVIEW_GOOD[rnd.nextInt(REVIEW_GOOD.length)]
                    : rating == 3 ? REVIEW_MID[rnd.nextInt(REVIEW_MID.length)]
                    : REVIEW_BAD[rnd.nextInt(REVIEW_BAD.length)];
            Review r = new Review();
            r.setBookingId(b.getId());
            r.setUserId(b.getUserId());
            r.setTargetType(BookingType.HOTEL);
            r.setTargetId(b.getTargetId());
            r.setRating(rating);
            r.setComment(cmt);
            r.setReviewerName(nameByUserId.getOrDefault(b.getUserId(), "Khách Dididi"));
            r.setStatus(ReviewStatus.PUBLISHED);
            batch.add(r);
            made++;
            if (batch.size() >= 200) { reviewRepository.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) reviewRepository.saveAll(batch);
        log.info("[x5] +{} đánh giá (điểm TB tính on-the-fly, không cần recompute)", made);
    }

    // ==================== 6) CỘNG ĐỒNG ====================

    private static final String[] CAPTIONS = {
            "Cuối tuần trốn phố về {city}, không khí dễ chịu thật sự! #dulich #{tag}",
            "Checklist ăn sập {city} coi như hoàn thành 90% 🍜 #foodtour #{tag}",
            "Hoàng hôn ở {city} đẹp không góc chết. #hoanghon #{tag} #dulich",
            "Lần đầu tới {city}, mọi người gợi ý thêm chỗ chơi với! #{tag} #hoidap",
            "Tips nhỏ: tới {city} nhớ thuê xe máy cho chủ động nha. #meodulich #{tag}",
            "3 ngày 2 đêm ở {city} mà vẫn thấy chưa đủ. Hẹn quay lại! #{tag} #travel",
            "Khách sạn kỳ này ưng quá, view xịn giá ổn. #review #{tag}",
            "Ai hỏi đi đâu chữa lành thì mình trả lời luôn: {city}. #chualanh #{tag}",
            "Đặc sản {city} đúng là danh bất hư truyền 😋 #amthuc #{tag}",
            "Sáng cà phê, chiều dạo biển — nhịp sống {city} là đây. #{tag} #slowlife",
            "Chuyến này đi theo lịch trình AI của Dididi gợi ý, hợp lý phết! #dididi #{tag}",
            "Săn được vé rẻ đi {city}, chốt kèo liền các bạn ơi. #vere #{tag}"};
    private static final String[] COMMENTS_POOL = {
            "Đẹp quá trời!", "Lưu lại liền, tháng sau đi đúng chỗ này.",
            "Cho xin tên khách sạn với bạn ơi!", "Giá phòng tầm bao nhiêu vậy bạn?",
            "Mình đi rồi, xác nhận đáng tiền nha.", "View xịn thế này mà giờ mới biết.",
            "Đi mùa này có đông không bạn?", "Cảm ơn tips của bạn nhé!",
            "Món này ăn ở quán nào ngon nhất nhỉ?", "Chốt kèo cuối tuần này luôn!",
            "Ảnh chụp máy gì mà nét vậy?", "Nhìn mà muốn xách balo đi liền."};

    private void seedSocial(int nPosts, int nComments, int nReactions, int nFollows,
                            List<User> users, List<Hotel> hotels) {
        // Hồ sơ cho tối đa 300 user seed (handle sinh tự động, an toàn trùng)
        int actors = Math.min(300, users.size());
        List<Long> actorIds = new ArrayList<>(actors);
        for (int i = 0; i < actors; i++) {
            actorIds.add(users.get(i).getId());
            profileService.getOrCreate(users.get(i).getId());
        }

        // Bài viết
        List<Post> posts = new ArrayList<>(nPosts);
        Map<Long, Integer> postsPerUser = new HashMap<>();
        for (int i = 0; i < nPosts; i++) {
            Long uid = actorIds.get(rnd.nextInt(actorIds.size()));
            Hotel h = hotels.get(rnd.nextInt(hotels.size()));
            String tag = normalizeTag(h.getCity());
            String caption = CAPTIONS[rnd.nextInt(CAPTIONS.length)]
                    .replace("{city}", h.getCity()).replace("{tag}", tag);
            boolean checkin = rnd.nextInt(3) == 0;
            Post p = new Post();
            p.setActorType(ActorType.USER);
            p.setActorId(uid);
            p.setAuthorUserId(uid);
            p.setCaption(caption);
            p.setType(checkin ? PostType.CHECKIN : PostType.STANDARD);
            p.setVisibility(PostVisibility.PUBLIC);
            p.setStatus(PostStatus.PUBLISHED);
            if (checkin) {
                p.setHotelId(h.getId());
                p.setPlaceName(h.getName());
                p.setLat(h.getLat());
                p.setLng(h.getLng());
            }
            postRepository.save(p); // cần id ngay để link hashtag
            hashtagService.linkHashtags(p.getId(), caption);
            posts.add(p);
            postsPerUser.merge(uid, 1, Integer::sum);
        }

        // Bình luận
        Map<Long, Integer> commentCount = new HashMap<>();
        List<Comment> cBatch = new ArrayList<>(200);
        for (int i = 0; i < nComments; i++) {
            Post p = posts.get(rnd.nextInt(posts.size()));
            Comment c = new Comment();
            c.setPostId(p.getId());
            c.setAuthorUserId(actorIds.get(rnd.nextInt(actorIds.size())));
            c.setContent(COMMENTS_POOL[rnd.nextInt(COMMENTS_POOL.length)]);
            c.setStatus(PostStatus.PUBLISHED);
            cBatch.add(c);
            commentCount.merge(p.getId(), 1, Integer::sum);
            if (cBatch.size() >= 200) { commentRepository.saveAll(cBatch); cBatch.clear(); }
        }
        if (!cBatch.isEmpty()) commentRepository.saveAll(cBatch);

        // Like (unique user+post)
        Map<Long, Integer> likeCount = new HashMap<>();
        Set<String> likedPairs = new HashSet<>();
        List<Reaction> rBatch = new ArrayList<>(200);
        int likes = 0;
        for (int i = 0; i < nReactions * 3 && likes < nReactions; i++) {
            Long uid = actorIds.get(rnd.nextInt(actorIds.size()));
            Post p = posts.get(rnd.nextInt(posts.size()));
            if (!likedPairs.add(uid + ":" + p.getId())) continue;
            Reaction r = new Reaction();
            r.setUserId(uid);
            r.setTargetType(ReactionTarget.POST);
            r.setTargetId(p.getId());
            r.setType(ReactionType.LIKE);
            rBatch.add(r);
            likeCount.merge(p.getId(), 1, Integer::sum);
            likes++;
            if (rBatch.size() >= 200) { reactionRepository.saveAll(rBatch); rBatch.clear(); }
        }
        if (!rBatch.isEmpty()) reactionRepository.saveAll(rBatch);

        // Follow đan chéo (unique follower+followee)
        Map<Long, Integer> followers = new HashMap<>();
        Map<Long, Integer> following = new HashMap<>();
        Set<String> followPairs = new HashSet<>();
        List<Follow> fBatch = new ArrayList<>(200);
        int follows = 0;
        for (int i = 0; i < nFollows * 3 && follows < nFollows; i++) {
            Long a = actorIds.get(rnd.nextInt(actorIds.size()));
            Long b = actorIds.get(rnd.nextInt(actorIds.size()));
            if (a.equals(b) || !followPairs.add(a + ">" + b)) continue;
            Follow f = new Follow();
            f.setFollowerUserId(a);
            f.setFolloweeType(ActorType.USER);
            f.setFolloweeId(b);
            f.setStatus(FollowStatus.ACTIVE);
            fBatch.add(f);
            followers.merge(b, 1, Integer::sum);
            following.merge(a, 1, Integer::sum);
            follows++;
            if (fBatch.size() >= 200) { followRepository.saveAll(fBatch); fBatch.clear(); }
        }
        if (!fBatch.isEmpty()) followRepository.saveAll(fBatch);

        // Đồng bộ counters (cột denormalized — ghi thẳng entity nên phải tự set)
        for (Post p : posts) {
            p.setLikeCount(likeCount.getOrDefault(p.getId(), 0));
            p.setCommentCount(commentCount.getOrDefault(p.getId(), 0));
        }
        postRepository.saveAll(posts);
        List<SocialProfile> profiles = profileRepository.findByUserIdIn(actorIds);
        for (SocialProfile sp : profiles) {
            sp.setPostsCount(sp.getPostsCount() + postsPerUser.getOrDefault(sp.getUserId(), 0));
            sp.setFollowersCount(sp.getFollowersCount() + followers.getOrDefault(sp.getUserId(), 0));
            sp.setFollowingCount(sp.getFollowingCount() + following.getOrDefault(sp.getUserId(), 0));
        }
        profileRepository.saveAll(profiles);
        log.info("[x5] +{} bài viết, +{} bình luận, +{} like, +{} follow (counters + hashtag đã đồng bộ)", posts.size(), nComments, likes, follows);
    }

    private static String normalizeTag(String city) {
        String n = java.text.Normalizer.normalize(city, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return n.isBlank() ? "vietnam" : n;
    }

    // ==================== 7) RẢI NGÀY TẠO (JPA Auditing ghi đè lúc persist) ====================

    private void spreadDates() {
        em.flush(); // đảm bảo mọi bản ghi đã xuống DB trước khi UPDATE native
        // Đơn: created_at = 3..45 ngày trước ngày đi -> doanh thu rải ~10 tháng
        em.createNativeQuery("""
                UPDATE bookings SET
                  created_at = TIMESTAMP(DATE_SUB(COALESCE(check_in, DATE(travel_date), CURRENT_DATE), INTERVAL FLOOR(3 + RAND()*42) DAY)),
                  updated_at = created_at
                WHERE public_code LIKE 'X5%'""").executeUpdate();
        em.createNativeQuery("""
                UPDATE payments p JOIN bookings b ON p.booking_id = b.id
                SET p.created_at = b.created_at, p.updated_at = b.created_at
                WHERE b.public_code LIKE 'X5%'""").executeUpdate();
        em.createNativeQuery("""
                UPDATE reviews r JOIN bookings b ON r.booking_id = b.id
                SET r.created_at = TIMESTAMP(DATE_ADD(b.check_out, INTERVAL 1 + FLOOR(RAND()*5) DAY)),
                    r.updated_at = r.created_at
                WHERE b.public_code LIKE 'X5%'""").executeUpdate();
        // Bài viết của user seed: rải ~120 ngày; bình luận sau bài viết 10'..3 ngày (không vượt hiện tại)
        em.createNativeQuery("""
                UPDATE social_posts p JOIN users u ON p.author_user_id = u.id
                SET p.created_at = TIMESTAMPADD(MINUTE, -FLOOR(RAND()*172800), NOW()), p.updated_at = p.created_at
                WHERE u.email LIKE 'x5user%'""").executeUpdate();
        em.createNativeQuery("""
                UPDATE social_comments c JOIN social_posts p ON c.post_id = p.id JOIN users u ON p.author_user_id = u.id
                SET c.created_at = LEAST(NOW(), TIMESTAMPADD(MINUTE, 10 + FLOOR(RAND()*4300), p.created_at)),
                    c.updated_at = c.created_at
                WHERE u.email LIKE 'x5user%'""").executeUpdate();
        log.info("[x5] Đã rải created_at (đơn/thanh toán/đánh giá/bài viết/bình luận) bằng native UPDATE.");
    }

    // ==================== helpers (theo HotelBulkSeeder) ====================

    private String uniqueName(String city, String suffix, Set<String> used) {
        String nameCity = "TP.HCM".equals(city) ? "Sài Gòn" : city;
        for (int tries = 0; tries < 60; tries++) {
            String nm = PRE[rnd.nextInt(PRE.length)] + " " + suffix + " " + nameCity;
            if (used.add(nm)) return nm;
        }
        String nm = PRE[rnd.nextInt(PRE.length)] + " " + suffix + " " + nameCity + " " + (used.size() + 1);
        used.add(nm);
        return nm;
    }

    private static PropertyType propertyTypeOf(String suffix) {
        String s = suffix.toLowerCase();
        if (s.contains("resort")) return PropertyType.RESORT;
        if (s.contains("villa")) return PropertyType.VILLA;
        if (s.contains("homestay")) return PropertyType.HOMESTAY;
        if (s.contains("residence") || s.contains("suites")) return PropertyType.APARTMENT;
        if (s.contains("inn") || s.contains("lodge")) return PropertyType.GUESTHOUSE;
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

    private Set<HotelTag> tagsFor(VnLocations.Loc loc, int star, Set<Amenity> ams) {
        Set<HotelTag> t = new LinkedHashSet<>();
        boolean beach = switch (loc.city) {
            case "Nha Trang", "Đà Nẵng", "Phú Quốc", "Vũng Tàu", "Hạ Long", "Hội An" -> true;
            default -> false;
        };
        if (beach) { t.add(HotelTag.SEA_VIEW); if (rnd.nextBoolean()) t.add(HotelTag.BEACHFRONT); }
        if (loc.city.equals("TP.HCM") || loc.city.equals("Hà Nội")) t.add(HotelTag.CITY_CENTER);
        if (loc.city.equals("Đà Lạt") || loc.city.equals("Sa Pa")) { t.add(HotelTag.QUIET); t.add(HotelTag.ROMANTIC); }
        if (star >= 5) t.add(HotelTag.LUXURY); else if (star == 3) t.add(HotelTag.BUDGET);
        if (ams.contains(Amenity.FAMILY_ROOM) || rnd.nextInt(3) == 0) t.add(HotelTag.FAMILY_FRIENDLY);
        if (ams.contains(Amenity.AIRPORT_SHUTTLE)) t.add(HotelTag.NEAR_AIRPORT);
        return t;
    }

    private static long round10k(double v) {
        return Math.max(1, Math.round(v / 10000.0)) * 10000L;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
