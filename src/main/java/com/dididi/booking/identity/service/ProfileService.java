package com.dididi.booking.identity.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ cho trang "Hồ sơ của tôi": đổi tên hiển thị, xác thực số điện thoại (OTP),
 * gỡ liên kết Google. (Đổi mật khẩu / quản lý thiết bị / xoá tài khoản nằm ở {@link AccountService}.)
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerification;

    public ProfileService(UserRepository userRepository, PhoneVerificationService phoneVerification) {
        this.userRepository = userRepository;
        this.phoneVerification = phoneVerification;
    }

    private User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.UNAUTHORIZED));
    }

    /** Đổi tên hiển thị. */
    @Transactional
    public void updateName(Long userId, String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("NAME_REQUIRED", "Vui lòng nhập tên hiển thị", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > 120) name = name.substring(0, 120);
        User u = get(userId);
        u.setFullName(name);
        userRepository.save(u);
    }

    /**
     * Cập nhật ngày sinh — dùng cho chương trình QUÀ SINH NHẬT.
     *
     * <p>CHỈ NHẬP MỘT LẦN. Trước đây khách sửa thoải mái, thậm chí xoá về null rồi nhập lại: đặt
     * ngày sinh = hôm nay là có quà ngay, năm sau lại đổi cho tiện — "sinh nhật" thành thứ khách tự
     * chọn chứ không phải dữ liệu thật. (Việc nhận TRÙNG trong cùng năm thì unique
     * (type, user_id, cycleKey=năm) của PromoGrant đã chặn sẵn.)</p>
     *
     * <p>Gõ nhầm thì admin sửa hộ được ({@link #adminSetBirthDate}) và lần sửa đó có ghi audit.</p>
     */
    @Transactional
    public void updateBirthDate(Long userId, java.time.LocalDate birthDate) {
        User u = get(userId);
        if (u.getBirthDate() != null) {
            throw new BusinessException("BIRTHDATE_LOCKED",
                    "Ngày sinh chỉ nhập được một lần. Nếu nhập nhầm, vui lòng liên hệ chăm sóc khách hàng để được sửa.",
                    HttpStatus.CONFLICT);
        }
        if (birthDate == null) {
            throw new BusinessException("INVALID_BIRTHDATE", "Vui lòng chọn ngày sinh", HttpStatus.BAD_REQUEST);
        }
        validateBirthDate(birthDate);
        u.setBirthDate(birthDate);
        userRepository.save(u);
    }

    /**
     * ADMIN đặt lại ngày sinh cho khách (đường thoát khi khách gõ nhầm). Trả về giá trị CŨ để bên
     * gọi ghi audit "từ gì sang gì" — thao tác này mở lại quyền nhận quà nên phải có dấu vết.
     */
    @Transactional
    public java.time.LocalDate adminSetBirthDate(Long userId, java.time.LocalDate birthDate) {
        if (birthDate != null) {
            validateBirthDate(birthDate);
        }
        User u = get(userId);
        java.time.LocalDate cu = u.getBirthDate();
        u.setBirthDate(birthDate);          // admin được phép xoá (null) để khách tự nhập lại
        userRepository.save(u);
        return cu;
    }

    private void validateBirthDate(java.time.LocalDate birthDate) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (birthDate.isAfter(today)) {
            throw new BusinessException("INVALID_BIRTHDATE", "Ngày sinh không thể ở tương lai", HttpStatus.BAD_REQUEST);
        }
        if (birthDate.isBefore(today.minusYears(120))) {
            throw new BusinessException("INVALID_BIRTHDATE", "Ngày sinh không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Bước 1 xác thực SĐT: lưu số (đánh dấu chưa xác thực) rồi gửi OTP.
     * Nếu số trùng số đã xác thực thì không cần làm gì.
     */
    @Transactional
    public void startPhoneVerification(Long userId, String phone) {
        String p = phone == null ? "" : phone.trim();
        if (!PhoneVerificationService.isValidPhone(p)) {
            throw new BusinessException("INVALID_PHONE",
                    "Số điện thoại không hợp lệ (VD: 0961xxxxxx hoặc +8496xxxxxxx)", HttpStatus.BAD_REQUEST);
        }
        User u = get(userId);
        // Đổi số -> phải xác thực lại.
        if (!p.equals(u.getPhone()) || !u.isPhoneVerified()) {
            u.setPhone(p);
            u.setPhoneVerified(false);
            userRepository.save(u);
        }
        phoneVerification.request(u);
    }

    /** Bước 2 xác thực SĐT: kiểm tra OTP và đánh dấu đã xác thực. */
    @Transactional
    public void confirmPhone(Long userId, String code) {
        User u = get(userId);
        if (u.getPhone() == null || u.getPhone().isBlank()) {
            throw new BusinessException("NO_PHONE", "Chưa có số điện thoại để xác thực", HttpStatus.BAD_REQUEST);
        }
        if (!phoneVerification.verify(userId, code)) {
            throw new BusinessException("INVALID_OTP", "Mã OTP không đúng hoặc đã hết hạn", HttpStatus.BAD_REQUEST);
        }
        u.setPhoneVerified(true);
        userRepository.save(u);
    }

    /** Gỡ liên kết Google. Chỉ cho phép khi user đã có mật khẩu để còn đăng nhập được. */
    @Transactional
    public void unlinkGoogle(Long userId) {
        User u = get(userId);
        if (!u.isGoogleLinked()) {
            throw new BusinessException("NOT_LINKED", "Tài khoản chưa liên kết Google", HttpStatus.BAD_REQUEST);
        }
        if (!u.isPasswordSet()) {
            throw new BusinessException("NO_PASSWORD",
                    "Hãy thiết lập mật khẩu trước khi gỡ liên kết Google (để vẫn đăng nhập được)", HttpStatus.BAD_REQUEST);
        }
        u.setGoogleLinked(false);
        userRepository.save(u);
    }
}
