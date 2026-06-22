package com.dididi.booking.review.repository;

import com.dididi.booking.review.domain.entity.ReviewImage;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findByReviewIdAndKindOrderBySortOrderAscIdAsc(Long reviewId, ReviewImageKind kind);
    long countByReviewIdAndKind(Long reviewId, ReviewImageKind kind);
}
