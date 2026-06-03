package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed du lieu mau cho moi truong dev de verify Phase 1 DoD.
 * Tai khoan: admin@dididi.local / Admin@123
 */
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           HotelRepository hotelRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@dididi.local")) {
            User admin = new User();
            admin.setEmail("admin@dididi.local");
            admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
            admin.setFullName("Dididi Admin");
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
            log.info("Seeded admin user: admin@dididi.local / Admin@123");
        }

        if (hotelRepository.count() == 0) {
            Hotel hotel = new Hotel();
            hotel.setName("Dididi Demo Hotel Saigon");
            hotel.setCity("Ho Chi Minh City");
            hotel.setAddress("1 Nguyen Hue, District 1");
            hotel.setDescription("Demo hotel seeded for Phase 1 verification.");
            hotel.setStarRating(4);
            hotel.setActive(true);
            hotelRepository.save(hotel);
            log.info("Seeded demo hotel.");
        }
    }
}
