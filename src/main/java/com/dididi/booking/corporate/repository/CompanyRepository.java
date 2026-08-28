package com.dididi.booking.corporate.repository;

import com.dididi.booking.corporate.domain.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCode(String code);
    Optional<Company> findByCode(String code);
    List<Company> findByActiveTrueOrderByName();
    List<Company> findAllByOrderByName();

    /**
     * P1-7 — khoá bi quan trên dòng công ty, dùng cho mọi thao tác đọc-rồi-ghi trên ngân sách
     * (trừ hạn mức khi đặt, hoàn hạn mức khi refund, nạp thêm tiền). Không có nó thì hai nhân viên
     * bấm đặt cùng lúc đều đọc được "còn đủ" rồi cùng ghi -> chi vượt hạn mức hợp đồng.
     * Cùng bài học với FIX-M9 (đổi điểm loyalty).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") Long id);
}
