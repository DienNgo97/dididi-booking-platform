package com.dididi.booking.identity.repository;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Tìm kiếm admin theo email / họ tên / SĐT, kèm lọc role/status tuỳ chọn (thanh tìm kiếm tab Người dùng). */
    @Query("""
            SELECT u FROM User u
            WHERE (lower(u.email) LIKE lower(concat('%', :q, '%'))
                   OR lower(u.fullName) LIKE lower(concat('%', :q, '%'))
                   OR u.phone LIKE concat('%', :q, '%'))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> adminSearch(@Param("q") String q,
                           @Param("role") Role role,
                           @Param("status") UserStatus status,
                           Pageable pageable);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Khoa bi quan (SELECT ... FOR UPDATE) tren dong user — dung lam "khoa nghiep vu" cho cac thao tac
     * doc-roi-ghi tren du lieu CUA CHINH user do (vd doi diem loyalty: doc so du -> kiem tra -> tru diem).
     * Cac request cung 1 user se XEP HANG; user khac khong bi anh huong. Xem M9 trong audit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    // ---- Admin (Phase 4b) ----
    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    // ---- Corporate B2B (Dot 3) ----
    List<User> findByCompanyIdOrderByEmail(Long companyId);

    // ---- Khuyến mãi cá nhân hoá ----
    /** Khách (CUSTOMER, đang hoạt động) có sinh nhật đúng ngày/tháng này. */
    @Query("select u from User u where u.role = :role and u.status = :status and u.birthDate is not null " +
            "and function('MONTH', u.birthDate) = :month and function('DAY', u.birthDate) = :day")
    List<User> findBirthdayCustomers(@Param("role") Role role,
                                     @Param("status") UserStatus status,
                                     @Param("month") int month,
                                     @Param("day") int day);

    List<User> findByRoleAndStatus(Role role, UserStatus status);
}
