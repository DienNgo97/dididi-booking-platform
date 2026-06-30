package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import com.dididi.booking.social.repository.SocialProfileRepository;
import com.dididi.booking.social.service.FollowService;
import com.dididi.booking.social.service.PostService;
import com.dididi.booking.social.service.SocialProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed dữ liệu Cộng đồng (mạng xã hội) để demo có nội dung: hồ sơ + bài viết gắn khách sạn + follow.
 * Bật bằng app.seed.demo=true (cùng cờ với DemoDataSeeder). Chạy SAU dữ liệu khách sạn/đánh giá.
 * Idempotent: nếu đã có social_profiles thì bỏ qua.
 */
@Component
@Profile("dev")
@Order(300)
public class DemoSocialSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSocialSeeder.class);

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final SocialProfileRepository profileRepository;
    private final SocialProfileService profileService;
    private final PostService postService;
    private final FollowService followService;

    @Value("${app.seed.demo:false}")
    private boolean enabled;

    private final Random rnd = new Random(20260625L);

    private static final String[] CAPTIONS = {
            "Hoàng hôn ở biển đẹp không thể rời mắt 🌅 #dididi #checkin",
            "Bữa sáng buffet siêu nhiều món, no nê cả ngày! #review #amthuc",
            "Phòng view thành phố, đêm lên đèn lung linh quá #travel #dididi",
            "Trốn phố về với núi rừng, không khí trong lành #sapa #nghiduong",
            "Hồ bơi vô cực nhìn ra biển, đỉnh thật sự #resort #checkin",
            "Một đêm yên tĩnh, dịch vụ chu đáo, sẽ quay lại #review",
            "Cà phê sáng bên bờ sông, chill hết nấc ☕ #dalat #travel",
            "Gợi ý khách sạn giá tốt mà xịn cho hội mê xê dịch #dididi #tips",
            "Check-in địa điểm sống ảo cực phẩm 📸 #checkin #travel",
            "Kỳ nghỉ gia đình trọn vẹn, các bé rất thích #family #nghiduong"
    };

    public DemoSocialSeeder(UserRepository userRepository, HotelRepository hotelRepository,
                            SocialProfileRepository profileRepository, SocialProfileService profileService,
                            PostService postService, FollowService followService) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.postService = postService;
        this.followService = followService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (profileRepository.count() > 0) {
            log.info("[DemoSocialSeeder] Đã có hồ sơ Cộng đồng -> bỏ qua.");
            return;
        }
        List<User> users = new ArrayList<>(
                userRepository.findByRole(Role.CUSTOMER, PageRequest.of(0, 12)).getContent());
        if (users.size() < 3) {
            log.warn("[DemoSocialSeeder] Quá ít người dùng CUSTOMER -> bỏ qua seed Cộng đồng.");
            return;
        }
        List<Hotel> hotels = hotelRepository.findByActiveTrue();
        if (hotels.isEmpty()) {
            log.warn("[DemoSocialSeeder] Không có khách sạn active -> bỏ qua.");
            return;
        }

        // 1) Hồ sơ
        for (User u : users) {
            profileService.getOrCreate(u.getId());
        }

        // 2) Bài viết cá nhân gắn khách sạn
        int posts = 0;
        for (int i = 0; i < 18; i++) {
            User author = users.get(rnd.nextInt(users.size()));
            Hotel h = hotels.get(rnd.nextInt(hotels.size()));
            String caption = CAPTIONS[rnd.nextInt(CAPTIONS.length)];
            try {
                postService.createPost(author.getId(), ActorType.USER, author.getId(), false,
                        caption, PostVisibility.PUBLIC, rnd.nextBoolean(),
                        h.getId(), null, null, null, null, null);
                posts++;
            } catch (Exception e) {
                log.debug("[DemoSocialSeeder] bỏ qua 1 bài: {}", e.getMessage());
            }
        }

        // 3) Bài dưới danh nghĩa khách sạn (nếu có KS DIRECT có chủ sở hữu)
        for (Hotel h : hotels) {
            if (h.getVendorId() != null && userRepository.findById(h.getVendorId()).isPresent()) {
                try {
                    postService.createPost(h.getVendorId(), ActorType.HOTEL, h.getId(), true,
                            "Chào mừng quý khách đến với " + h.getName() + "! Đặt phòng ngay trên Dididi 🏨 #khachsan",
                            PostVisibility.PUBLIC, false, h.getId(), null, null, null, null, null);
                    posts++;
                } catch (Exception e) {
                    log.debug("[DemoSocialSeeder] bỏ qua bài KS: {}", e.getMessage());
                }
                break;
            }
        }

        // 4) Follow chéo: mỗi người theo dõi 2 người kế tiếp
        int follows = 0;
        for (int i = 0; i < users.size(); i++) {
            for (int k = 1; k <= 2; k++) {
                User a = users.get(i);
                User b = users.get((i + k) % users.size());
                if (!a.getId().equals(b.getId())) {
                    try {
                        followService.follow(a.getId(), ActorType.USER, b.getId());
                        follows++;
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        log.info("[DemoSocialSeeder] HOÀN TẤT: {} hồ sơ, {} bài, {} lượt follow.",
                users.size(), posts, follows);
    }
}
