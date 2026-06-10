package com.dididi.booking.review.repository;

import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    java.util.Optional<Review> findByBookingId(Long bookingId);

    // ---- Cong khai: chi review da PUBLISHED ----
    Page<Review> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            BookingType targetType, Long targetId, ReviewStatus status, Pageable pageable);

    @Query("select avg(r.rating) from Review r " +
            "where r.targetType = ?1 and r.targetId = ?2 and r.status = ?3")
    Double averageRating(BookingType targetType, Long targetId, ReviewStatus status);

    // ---- Admin kiem duyet ----
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    // ---- Vendor: tat ca review tren khach san cua minh (moi trang thai) ----
    Page<Review> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            BookingType targetType, Long targetId, Pageable pageable);
}
