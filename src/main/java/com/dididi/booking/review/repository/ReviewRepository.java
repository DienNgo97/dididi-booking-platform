package com.dididi.booking.review.repository;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Tìm kiếm admin theo tên khách / nội dung / phản hồi vendor, kèm lọc status (thanh tìm kiếm tab Đánh giá). */
    @Query("""
            SELECT r FROM Review r
            WHERE (lower(r.reviewerName) LIKE lower(concat('%', :q, '%'))
                   OR lower(r.comment) LIKE lower(concat('%', :q, '%'))
                   OR lower(r.vendorReply) LIKE lower(concat('%', :q, '%')))
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<Review> adminSearch(@org.springframework.data.repository.query.Param("q") String q,
                             @org.springframework.data.repository.query.Param("status") ReviewStatus status,
                             Pageable pageable);

    boolean existsByBookingId(Long bookingId);

    java.util.Optional<Review> findByBookingId(Long bookingId);

    // ---- Cong khai: chi review da PUBLISHED ----
    Page<Review> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            BookingType targetType, Long targetId, ReviewStatus status, Pageable pageable);

    @Query("select avg(r.rating) from Review r " +
            "where r.targetType = ?1 and r.targetId = ?2 and r.status = ?3")
    Double averageRating(BookingType targetType, Long targetId, ReviewStatus status);

    /** Diem trung binh theo LO (fix M5 N+1 trang /hotels): 1 query GROUP BY thay vi 1 query/khach san. */
    @Query("select r.targetId, avg(r.rating) from Review r " +
            "where r.targetType = ?1 and r.status = ?2 and r.targetId in ?3 group by r.targetId")
    java.util.List<Object[]> averageRatings(BookingType targetType, ReviewStatus status,
                                            java.util.Collection<Long> targetIds);

    // ---- Admin kiem duyet ----
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    // ---- Vendor: tat ca review tren khach san cua minh (moi trang thai) ----
    Page<Review> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            BookingType targetType, Long targetId, Pageable pageable);
}
