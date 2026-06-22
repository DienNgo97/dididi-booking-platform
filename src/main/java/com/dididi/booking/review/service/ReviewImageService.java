package com.dididi.booking.review.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.review.api.dto.ReviewImageDto;
import com.dididi.booking.review.domain.entity.Review;
import com.dididi.booking.review.domain.entity.ReviewImage;
import com.dididi.booking.review.domain.enums.ReviewImageKind;
import com.dididi.booking.review.repository.ReviewImageRepository;
import com.dididi.booking.review.repository.ReviewRepository;
import com.dididi.booking.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Quan ly anh dinh kem danh gia (REVIEW) va phan hoi vendor (REPLY) - luu MinIO qua StorageService. */
@Service
public class ReviewImageService {

    public static final int MAX_REVIEW_IMAGES = 5;
    public static final int MAX_REPLY_IMAGES = 3;

    private final ReviewImageRepository imageRepository;
    private final ReviewRepository reviewRepository;
    private final HotelRepository hotelRepository;
    private final StorageService storageService;

    public ReviewImageService(ReviewImageRepository imageRepository, ReviewRepository reviewRepository,
                              HotelRepository hotelRepository, StorageService storageService) {
        this.imageRepository = imageRepository;
        this.reviewRepository = reviewRepository;
        this.hotelRepository = hotelRepository;
        this.storageService = storageService;
    }

    /** Khach dinh anh vao danh gia cua chinh minh. */
    @Transactional
    public List<ReviewImageDto> attachReviewImages(Long reviewId, Long userId, MultipartFile[] files) {
        Review r = getReview(reviewId);
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền thêm ảnh cho đánh giá này", HttpStatus.FORBIDDEN);
        }
        return store(reviewId, ReviewImageKind.REVIEW, files, MAX_REVIEW_IMAGES);
    }

    /** Vendor dinh anh vao phan hoi - chi tren khach san cua minh. */
    @Transactional
    public List<ReviewImageDto> attachReplyImages(Long reviewId, Long vendorUserId, MultipartFile[] files) {
        Review r = getReview(reviewId);
        Hotel hotel = hotelRepository.findByVendorId(vendorUserId)
                .orElseThrow(() -> new BusinessException("NO_HOTEL", "Tài khoản chưa gắn khách sạn nào", HttpStatus.NOT_FOUND));
        if (!hotel.getId().equals(r.getTargetId())) {
            throw new BusinessException("FORBIDDEN", "Đánh giá không thuộc khách sạn của bạn", HttpStatus.FORBIDDEN);
        }
        return store(reviewId, ReviewImageKind.REPLY, files, MAX_REPLY_IMAGES);
    }

    private List<ReviewImageDto> store(Long reviewId, ReviewImageKind kind, MultipartFile[] files, int max) {
        if (files == null || files.length == 0) {
            return listDtos(reviewId, kind);
        }
        long existing = imageRepository.countByReviewIdAndKind(reviewId, kind);
        long incoming = 0;
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                incoming++;
            }
        }
        if (existing + incoming > max) {
            throw new BusinessException("TOO_MANY_IMAGES",
                    "Tối đa " + max + " ảnh" + (kind == ReviewImageKind.REPLY ? " cho phản hồi" : " cho đánh giá"),
                    HttpStatus.BAD_REQUEST);
        }
        int order = (int) existing;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String key = storageService.upload(f, "reviews/" + reviewId);
            ReviewImage img = new ReviewImage();
            img.setReviewId(reviewId);
            img.setKind(kind);
            img.setObjectKey(key);
            img.setContentType(f.getContentType());
            img.setSortOrder(order++);
            imageRepository.save(img);
        }
        return listDtos(reviewId, kind);
    }

    public List<ReviewImageDto> listDtos(Long reviewId, ReviewImageKind kind) {
        return imageRepository.findByReviewIdAndKindOrderBySortOrderAscIdAsc(reviewId, kind)
                .stream().map(ReviewImageDto::from).toList();
    }

    /** Danh sach URL (cho Thymeleaf / DTO). */
    public List<String> listUrls(Long reviewId, ReviewImageKind kind) {
        return imageRepository.findByReviewIdAndKindOrderBySortOrderAscIdAsc(reviewId, kind)
                .stream().map(i -> ReviewImageDto.from(i).url()).toList();
    }

    /** Doc bytes 1 anh de serve trong the <img>. */
    public StorageService.StoredObject load(Long reviewId, Long imageId) {
        ReviewImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND));
        if (!img.getReviewId().equals(reviewId)) {
            throw new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND);
        }
        return storageService.load(img.getObjectKey());
    }

    private Review getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đánh giá", HttpStatus.NOT_FOUND));
    }
}
