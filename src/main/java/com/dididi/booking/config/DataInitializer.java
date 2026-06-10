package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.repository.CompanyRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
    private final CompanyRepository companyRepository;
    private final com.dididi.booking.voucher.repository.VoucherRepository voucherRepository;

    // Seed admin password - PROD/CI lay tu ENV APP_ADMIN_PASSWORD; fallback dev de chay local.
    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           HotelRepository hotelRepository,
                           PasswordEncoder passwordEncoder,
                           CompanyRepository companyRepository,
                           com.dididi.booking.voucher.repository.VoucherRepository voucherRepository) {
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.passwordEncoder = passwordEncoder;
        this.companyRepository = companyRepository;
        this.voucherRepository = voucherRepository;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@dididi.local")) {
            User admin = new User();
            admin.setEmail("admin@dididi.local");
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setFullName("Dididi Admin");
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
            log.info("Seeded admin user: admin@dididi.local (password tu app.admin.password / ENV APP_ADMIN_PASSWORD)");
        }

        if (!userRepository.existsByEmail("superadmin@dididi.local")) {
            User sa = new User();
            sa.setEmail("superadmin@dididi.local");
            sa.setPasswordHash(passwordEncoder.encode("Super@123"));
            sa.setFullName("Dididi Super Admin");
            sa.setRole(Role.SUPER_ADMIN);
            sa.setStatus(UserStatus.ACTIVE);
            userRepository.save(sa);
            log.info("Seeded super admin: superadmin@dididi.local / Super@123");
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

        // ---- Corporate B2B (Dot 3): cong ty + nhan vien demo ----
        if (!companyRepository.existsByCode("DDCORP")) {
            Company corp = new Company();
            corp.setName("Dididi Corp");
            corp.setCode("DDCORP");
            corp.setBudgetTotal(new BigDecimal("20000000"));
            corp.setBudgetUsed(BigDecimal.ZERO);
            corp.setContactEmail("finance@dididi.local");
            corp.setTaxCode("0301234567");
            corp.setAddress("123 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM");
            corp.setApprovalThreshold(new BigDecimal("5000000"));
            corp.setActive(true);
            companyRepository.save(corp);
            log.info("Seeded company: Dididi Corp (DDCORP) budget 20,000,000 VND");
        }
        if (!userRepository.existsByEmail("employee@dididi.local")) {
            Long companyId = companyRepository.findByCode("DDCORP").map(Company::getId).orElse(null);
            User emp = new User();
            emp.setEmail("employee@dididi.local");
            emp.setPasswordHash(passwordEncoder.encode("Employee@123"));
            emp.setFullName("Nhan vien Dididi Corp");
            emp.setRole(Role.CUSTOMER);
            emp.setStatus(UserStatus.ACTIVE);
            emp.setCompanyId(companyId);
            userRepository.save(emp);
            log.info("Seeded employee: employee@dididi.local / Employee@123 (company DDCORP)");
        }

        // ---- Voucher demo ----
        if (voucherRepository.count() == 0) {
            com.dididi.booking.voucher.domain.Voucher w = new com.dididi.booking.voucher.domain.Voucher();
            w.setCode("WELCOME10");
            w.setDescription("Giảm 10% (tối đa 200.000đ) cho đơn từ 500.000đ");
            w.setDiscountType(com.dididi.booking.voucher.domain.VoucherDiscountType.PERCENT);
            w.setDiscountValue(new BigDecimal("10"));
            w.setMaxDiscount(new BigDecimal("200000"));
            w.setMinOrderAmount(new BigDecimal("500000"));
            w.setPerUserLimit(1);
            w.setActive(true);
            voucherRepository.save(w);

            com.dididi.booking.voucher.domain.Voucher s = new com.dididi.booking.voucher.domain.Voucher();
            s.setCode("SALE100K");
            s.setDescription("Giảm 100.000đ cho đơn từ 1.000.000đ");
            s.setDiscountType(com.dididi.booking.voucher.domain.VoucherDiscountType.FIXED);
            s.setDiscountValue(new BigDecimal("100000"));
            s.setMinOrderAmount(new BigDecimal("1000000"));
            s.setActive(true);
            voucherRepository.save(s);
            log.info("Seeded vouchers: WELCOME10, SALE100K");
        }
    }
}
