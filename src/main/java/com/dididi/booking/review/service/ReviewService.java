package com.dididi.booking.review.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.enums.ReviewStatus;
import com.dididi.booking.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReviewService {

    private static final DateTimeFormatter TRIP_END_FMT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final boolean autoPublish;

    public ReviewService(ReviewRepository reviewRepository, BookingRepository bookingRepository,
                         UserRepository userRepository, HotelRepository hotelRepository,
                         @Value("${app.review.auto-publish:true}") boolean autoPublish) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.autoPublish = autoPublish;
    }

    // ---------- Khach tao danh gia ----------
    @Transactional
    public Review create(Long userId, String bookingCode, int rating, String comment) {
        Booking b = bookingRepository.findByPublicCode(bookingCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (!b.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền đánh giá đơn này", HttpStatus.FORBIDDEN);
        }
        if (b.getType() == BookingType.FLIGHT) {
            throw new BusinessException("REVIEW_DISABLED", "Hiện chưa hỗ trợ đánh giá chuyến bay", HttpStatus.BAD_REQUEST);
        }
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("NOT_REVIEWABLE", "Chỉ đánh giá được đơn đã xác nhận", HttpStatus.CONFLICT);
        }
        requireTripEnded(b);
        if (b.getTargetId() == null) {
            throw new BusinessException("NOT_REVIEWABLE", "Đơn này không hỗ trợ đánh giá", HttpStatus.CONFLICT);
        }
        if (reviewRepository.existsByBookingId(b.getId())) {
            throw new BusinessException("ALREADY_REVIEWED", "Đơn này đã được đánh giá", HttpStatus.CONFLICT);
        }

        User u = userRepository.findById(userId).orElse(null);
        Review r = new Review();
        r.setBookingId(b.getId());
        r.setUserId(userId);
        r.setTargetType(b.getType());
        r.setTargetId(b.getTargetId());
        r.setRating(rating);
        r.setComment(comment);
        r.setReviewerName(u != null && u.getFullName() != null ? u.getFullName() : "Khách");
        r.setStatus(autoPublish ? ReviewStatus.PUBLISHED : ReviewStatus.PENDING);
        return reviewRepository.save(r);
    }

    /**
     * Chi cho danh gia sau khi chuyen di ket thuc (tang tinh chan thuc, tranh danh gia ao).
     * KS qua dem: sau 12h trua ngay tra phong. KS theo gio: sau gio tra. Ve bay: sau gio bay.
     */
    private void requireTripEnded(Booking b) {
        LocalDateTime end = tripEnd(b);
        if (end != null && LocalDateTime.now().isBefore(end)) {
            throw new BusinessException("TRIP_NOT_ENDED",
                    "Chỉ có thể đánh giá sau khi kết thúc chuyến đi (sau " + end.format(TRIP_END_FMT) + ")",
                    HttpStatus.CONFLICT);
        }
    }

    /** Thoi diem ket thuc chuyen di; null = khong xac dinh duoc -> khong chan. */
    private LocalDateTime tripEnd(Booking b) {
        if (b.getType() == BookingType.HOTEL) {
            if (b.isDayUse() && b.getCheckIn() != null && b.getCheckOutTime() != null) {
                return b.getCheckIn().atTime(b.getCheckOutTime());     // KS theo gio: gio tra phong
            }
            if (b.getCheckOut() != null) {
                return b.getCheckOut().atTime(LocalTime.NOON);         // KS qua dem: 12h trua ngay tra
            }
            if (b.getCheckIn() != null) {
                return b.getCheckIn().plusDays(1).atTime(LocalTime.NOON);
            }
            return null;
        }
        if (b.getTravelDate() != null) {
            return b.getTravelDate();                                  // Ve bay: sau gio bay
        }
        return null;
    }

    // ---------- Cong khai (chi PUBLISHED) ----------
    /** Diem trung binh (lam tron 1 chu so) tinh tren review da PUBLISHED. Khong co -> 0.0. */
    public double averageRating(BookingType type, Long targetId) {
        Double avg = reviewRepository.averageRating(type, targetId, ReviewStatus.PUBLISHED);
        if (avg == null) {
            return 0.0;
        }
        return Math.round(avg * 10.0) / 10.0;
    }

    public Page<Review> list(BookingType type, Long targetId, int page, int size) {
        return reviewRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
                type, targetId, ReviewStatus.PUBLISHED, PageRequest.of(page, size));
    }

    /** Review (neu co) cua mot don - dung cho web hien thi "ban da danh gia". */
    public java.util.Optional<Review> reviewForBooking(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId);
    }

    // ---------- Vendor tra loi ----------
    @Transactional
    public Review vendorReply(Long reviewId, Long vendorUserId, String reply) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
        if (r.getTargetType() != BookingType.HOTEL) {
            throw new BusinessException("NOT_HOTEL_REVIEW", "Chỉ trả lời được đánh giá khách sạn", HttpStatus.BAD_REQUEST);
        }
        Hotel hotel = hotelRepository.findByVendorId(vendorUserId)
                .orElseThrow(() -> new BusinessException("NO_HOTEL", "Tài khoản chưa gắn khách sạn nào", HttpStatus.NOT_FOUND));
        if (!hotel.getId().equals(r.getTargetId())) {
            throw new BusinessException("FORBIDDEN", "Đánh giá không thuộc khách sạn của bạn", HttpStatus.FORBIDDEN);
        }
        r.setVendorReply(reply);
        r.setVendorReplyAt(Instant.now());
        return reviewRepository.save(r);
    }

    /** Tat ca review tren khach san cua vendor (moi trang thai) - cho UI vendor. */
    public Page<Review> listForVendor(Long vendorUserId, int page, int size) {
        Hotel hotel = hotelRepository.findByVendorId(vendorUserId).orElse(null);
        if (hotel == null) {
            return Page.empty(PageRequest.of(page, size));
        }
        return reviewRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                BookingType.HOTEL, hotel.getId(), PageRequest.of(page, size));
    }

    // ---------- Admin kiem duyet ----------
    public Page<Review> listForAdmin(ReviewStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return (status == null)
                ? reviewRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reviewRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public Review setStatus(Long reviewId, ReviewStatus status) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
        r.setStatus(status);
        return reviewRepository.save(r);
    }

    @Transactional
    public void delete(Long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new BusinessException("NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND);
        }
        reviewRepository.deleteById(reviewId);
    }
}
