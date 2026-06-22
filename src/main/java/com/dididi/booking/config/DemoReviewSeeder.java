package com.dididi.booking.config;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import com.dididi.booking.review.repository.ReviewRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed DỮ LIỆU TRỰC QUAN: 500 đơn đặt khách sạn + 500 đánh giá, rải rác trên các khách sạn
 * đang có (active) trong DB. Mục đích làm phong phú dữ liệu để hiển thị (vd: Top 10 KS theo
 * điểm trung bình ở trang chủ) trông sinh động hơn.
 *
 * BẬT bằng cờ: app.seed.reviews=true  (mặc định false). Chạy SAU DemoDataSeeder (@Order 200)
 * nên các KS DIRECT do seeder chính tạo đã sẵn sàng. Cũng rải lên mọi KS active khác đang có
 * trong DB (kể cả KS đồng bộ từ PMS đã lưu trước đó).
 *
 * Idempotent: đánh dấu bằng đơn có publicCode "SR0000000001"; nếu đã tồn tại thì bỏ qua.
 * Chạy trong 1 transaction (all-or-nothing).
 */
@Component
@Profile("dev")
@Order(200)
public class DemoReviewSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoReviewSeeder.class);

    private static final int NUM = 500;
    private static final String MARKER = "SR0000000001";

    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.reviews:false}")
    private boolean enabled;

    private final Random rnd = new Random(20260620L);
    private long codeSeq = 1;

    private static final String[] LAST = {"Nguyễn","Trần","Lê","Phạm","Hoàng","Huỳnh","Phan","Vũ","Võ","Đặng","Bùi","Đỗ","Hồ","Ngô"};
    private static final String[] FIRST = {"An","Bình","Châu","Dũng","Hà","Hải","Hùng","Lan","Linh","Minh","Nam","Ngọc","Phong","Quân","Sơn","Tâm","Thảo","Trang","Tuấn","Vân"};

    // Nhận xét theo cảm xúc (chọn theo số sao)
    private static final String[] CMT_POS = {
        "Phòng sạch sẽ, nhân viên thân thiện, sẽ quay lại!",
        "Vị trí thuận tiện, view đẹp, đáng đồng tiền.",
        "Dịch vụ tuyệt vời, bữa sáng phong phú.",
        "Khách sạn đẹp hơn cả mong đợi, rất hài lòng.",
        "Giường êm, yên tĩnh, check-in nhanh gọn.",
        "Nhân viên nhiệt tình, phòng rộng rãi thoáng mát."
    };
    private static final String[] CMT_MID = {
        "Ổn so với giá tiền, vài chỗ cần cải thiện.",
        "Phòng tạm được, hơi ồn vào buổi tối.",
        "Tạm ổn cho một đêm nghỉ, bữa sáng bình thường.",
        "Vị trí ok nhưng wifi hơi yếu."
    };
    private static final String[] CMT_NEG = {
        "Phòng chưa được sạch lắm, cần dọn kỹ hơn.",
        "Cách âm kém, ngủ không ngon.",
        "Dịch vụ chậm, chờ check-in hơi lâu."
    };

    public DemoReviewSeeder(HotelRepository hotelRepository, UserRepository userRepository,
                            BookingRepository bookingRepository, ReviewRepository reviewRepository,
                            PasswordEncoder passwordEncoder) {
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (bookingRepository.findByPublicCode(MARKER).isPresent()) {
            log.info("[DemoReviewSeeder] Đã seed trước đó (marker {} tồn tại) -> bỏ qua.", MARKER);
            return;
        }
        List<Hotel> hotels = hotelRepository.findByActiveTrue();
        if (hotels.isEmpty()) {
            log.warn("[DemoReviewSeeder] Không có khách sạn active nào -> bỏ qua. " +
                    "Hãy seed/đồng bộ khách sạn trước (vd app.seed.demo=true).");
            return;
        }

        // Pool người đánh giá: dùng customer hiện có; nếu quá ít thì tạo thêm vài tài khoản demo.
        List<User> reviewers = new ArrayList<>(
                userRepository.findByRole(Role.CUSTOMER, PageRequest.of(0, 300)).getContent());
        for (int i = 1; reviewers.size() < 20 && i <= 30; i++) {
            String email = String.format("reviewer%03d@dididi.local", i);
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            User u = new User();
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode("Reviewer@123"));
            u.setFullName(person());
            u.setRole(Role.CUSTOMER);
            u.setStatus(UserStatus.ACTIVE);
            reviewers.add(userRepository.save(u));
        }

        log.info("[DemoReviewSeeder] Bắt đầu seed {} đơn KS + {} đánh giá trên {} khách sạn, {} người đánh giá...",
                NUM, NUM, hotels.size(), reviewers.size());

        // 1) Tạo 500 đơn KS (lưu trước để lấy id gắn vào review)
        List<Booking> bookings = new ArrayList<>(NUM);
        List<Hotel> hotelOf = new ArrayList<>(NUM);
        List<User> userOf = new ArrayList<>(NUM);
        for (int i = 0; i < NUM; i++) {
            Hotel h = hotels.get(rnd.nextInt(hotels.size()));
            User u = reviewers.get(rnd.nextInt(reviewers.size()));
            bookings.add(hotelBooking(h, u.getId()));
            hotelOf.add(h);
            userOf.add(u);
        }
        List<Booking> saved = bookingRepository.saveAll(bookings);

        // 2) Tạo 500 review (1 review / 1 đơn — đúng ràng buộc unique booking_id)
        List<Review> reviews = new ArrayList<>(NUM);
        for (int i = 0; i < NUM; i++) {
            reviews.add(review(saved.get(i), hotelOf.get(i), userOf.get(i)));
        }
        reviewRepository.saveAll(reviews);

        log.info("[DemoReviewSeeder] HOÀN TẤT. Tổng đơn={}, tổng review={}.",
                bookingRepository.count(), reviewRepository.count());
    }

    // ----- helpers -----

    private String person() {
        return LAST[rnd.nextInt(LAST.length)] + " " + FIRST[rnd.nextInt(FIRST.length)] + " " + FIRST[rnd.nextInt(FIRST.length)];
    }

    private String nextCode() {
        return String.format("SR%010d", codeSeq++);
    }

    /** Đơn KS đã hoàn tất (ngày ở trong quá khứ) để hợp lý với việc đã có đánh giá. */
    private Booking hotelBooking(Hotel h, Long userId) {
        int nights = 1 + rnd.nextInt(4);
        LocalDate checkIn = LocalDate.now().minusDays(3 + rnd.nextInt(150));
        LocalDate checkOut = checkIn.plusDays(nights);
        BigDecimal price = h.getMinPrice() != null ? h.getMinPrice() : BigDecimal.valueOf(800000);
        Booking b = new Booking();
        b.setPublicCode(nextCode());
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTargetId(h.getId());
        b.setTitle(h.getName());
        b.setCheckIn(checkIn);
        b.setCheckOut(checkOut);
        b.setQuantity(1);
        b.setAmount(price.multiply(BigDecimal.valueOf(nights)));
        b.setCurrency("VND");
        b.setStatus(BookingStatus.CONFIRMED);
        return b;
    }

    private Review review(Booking b, Hotel h, User u) {
        int rating = weightedRating();
        String comment;
        if (rating >= 4) {
            comment = CMT_POS[rnd.nextInt(CMT_POS.length)];
        } else if (rating == 3) {
            comment = CMT_MID[rnd.nextInt(CMT_MID.length)];
        } else {
            comment = CMT_NEG[rnd.nextInt(CMT_NEG.length)];
        }
        Review r = new Review();
        r.setBookingId(b.getId());
        r.setUserId(u.getId());
        r.setTargetType(BookingType.HOTEL);
        r.setTargetId(h.getId());
        r.setRating(rating);
        r.setComment(comment);
        r.setReviewerName(u.getFullName() != null ? u.getFullName() : "Khách");
        r.setStatus(ReviewStatus.PUBLISHED);
        return r;
    }

    /** Phân phối sao thiên về cao (4-5) nhưng vẫn có 1-3 để điểm trung bình giữa các KS đa dạng. */
    private int weightedRating() {
        int x = rnd.nextInt(100);
        if (x < 48) return 5;
        if (x < 78) return 4;
        if (x < 92) return 3;
        if (x < 98) return 2;
        return 1;
    }
}
