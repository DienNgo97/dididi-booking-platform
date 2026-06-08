package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomInventory;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomInventoryRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seed 1 vendor mau (DIRECT hotel + 2 room type + ton kho 7 ngay) cho moi truong dev.
 * Tai khoan: vendor1@dididi.local / Vendor@123
 */
@Component
@Profile("dev")
@Order(20)
public class VendorSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VendorSeeder.class);

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final PasswordEncoder passwordEncoder;

    public VendorSeeder(UserRepository userRepository, HotelRepository hotelRepository,
                        RoomTypeRepository roomTypeRepository, RoomInventoryRepository roomInventoryRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail("vendor1@dididi.local")) {
            return;
        }
        User vendor = new User();
        vendor.setEmail("vendor1@dididi.local");
        vendor.setPasswordHash(passwordEncoder.encode("Vendor@123"));
        vendor.setFullName("Chu nha nghi Bien Xanh");
        vendor.setRole(Role.VENDOR);
        vendor.setStatus(UserStatus.ACTIVE);
        userRepository.save(vendor);

        Hotel hotel = new Hotel();
        hotel.setName("Nha nghi Bien Xanh");
        hotel.setCity("Nha Trang");
        hotel.setAddress("12 Tran Phu, Nha Trang");
        hotel.setDescription("Khach san nho do vendor tu quan tren Dididi.");
        hotel.setStarRating(3);
        hotel.setActive(true);
        hotel.setSource(HotelSource.DIRECT);
        hotel.setVendorId(vendor.getId());
        hotelRepository.save(hotel);

        RoomType standard = newRoomType(hotel.getId(), "Phong Standard", 2, new BigDecimal("600000"), 10);
        RoomType deluxe = newRoomType(hotel.getId(), "Phong Deluxe", 3, new BigDecimal("1200000"), 5);
        roomTypeRepository.save(standard);
        roomTypeRepository.save(deluxe);

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            roomInventoryRepository.save(newInv(standard.getId(), d, 10));
            roomInventoryRepository.save(newInv(deluxe.getId(), d, 5));
        }
        log.info("Seeded vendor1@dididi.local + DIRECT hotel '{}' (2 room types, 7 ngay ton kho).", hotel.getName());
    }

    private RoomType newRoomType(Long hotelId, String name, int capacity, BigDecimal price, int total) {
        RoomType r = new RoomType();
        r.setHotelId(hotelId);
        r.setName(name);
        r.setCapacity(capacity);
        r.setBasePrice(price);
        r.setCurrency("VND");
        r.setTotalRooms(total);
        return r;
    }

    private RoomInventory newInv(Long roomTypeId, LocalDate date, int avail) {
        RoomInventory inv = new RoomInventory();
        inv.setRoomTypeId(roomTypeId);
        inv.setDate(date);
        inv.setAvailableRooms(avail);
        return inv;
    }
}
