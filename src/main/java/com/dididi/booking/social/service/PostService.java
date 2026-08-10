package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/** Tao/xoa bai, kiem tra quyen xem. Tac gia co the la ca nhan hoac trang khach san. */
@Service
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final SocialMediaService mediaService;
    private final SocialProfileService profileService;
    private final HotelRepository hotelRepository;
    private final FollowService followService;
    private final HashtagService hashtagService;
    private final MentionService mentionService;
    private final NotificationService notificationService;

    public PostService(PostRepository postRepository, SocialMediaService mediaService,
                       SocialProfileService profileService, HotelRepository hotelRepository,
                       FollowService followService, HashtagService hashtagService,
                       MentionService mentionService, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.mediaService = mediaService;
        this.profileService = profileService;
        this.hotelRepository = hotelRepository;
        this.followService = followService;
        this.hashtagService = hashtagService;
        this.mentionService = mentionService;
        this.notificationService = notificationService;
    }

    public Post createPost(Long authorUserId, ActorType actorType, Long actorId, boolean isAdmin,
                           String caption, PostVisibility visibility, boolean checkin,
                           Long hotelId, Long bookingId, String placeName, Double lat, Double lng,
                           MultipartFile[] files) {
        boolean hasMedia = hasMedia(files);
        if ((caption == null || caption.isBlank()) && !hasMedia) {
            throw new BusinessException("EMPTY_POST", "Bài viết cần có nội dung hoặc ảnh/video", HttpStatus.BAD_REQUEST);
        }
        if (actorType == ActorType.HOTEL) {
            Hotel hotel = hotelRepository.findById(actorId)
                    .orElseThrow(() -> new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
            if (!(isAdmin || authorUserId.equals(hotel.getVendorId()))) {
                throw new BusinessException("FORBIDDEN", "Bạn không có quyền đăng dưới danh nghĩa khách sạn này", HttpStatus.FORBIDDEN);
            }
        }

        Post p = new Post();
        p.setActorType(actorType);
        p.setActorId(actorId);
        p.setAuthorUserId(authorUserId);
        p.setCaption(caption == null ? null : caption.trim());
        p.setVisibility(visibility != null ? visibility : PostVisibility.PUBLIC);
        p.setStatus(PostStatus.PUBLISHED);
        p.setBookingId(bookingId);
        p.setType(checkin ? PostType.CHECKIN : PostType.STANDARD);

        if (hotelId != null) {
            p.setHotelId(hotelId);
            Hotel h = hotelRepository.findById(hotelId).orElse(null);
            if (h != null) {
                if (placeName == null || placeName.isBlank()) {
                    p.setPlaceName(h.getName() + (h.getCity() != null ? ", " + h.getCity() : ""));
                } else {
                    p.setPlaceName(placeName.trim());
                }
                if (checkin && h.hasGeo()) {
                    p.setLat(h.getLat());
                    p.setLng(h.getLng());
                }
            }
        } else {
            if (placeName != null && !placeName.isBlank()) {
                p.setPlaceName(placeName.trim());
            }
            if (lat != null && lng != null) {
                p.setLat(lat);
                p.setLng(lng);
            }
        }

        Post saved = postRepository.save(p);
        mediaService.attachPostMedia(saved.getId(), files);
        if (actorType == ActorType.USER) {
            profileService.adjustPostsCount(authorUserId, 1);
        }
        hashtagService.linkHashtags(saved.getId(), saved.getCaption());
        mentionService.process(saved.getId(), authorUserId, saved.getCaption());
        return saved;
    }

    /** Đăng lại (repost) 1 bài. Idempotent: đã repost thì trả về bản cũ. */
    public Post createRepost(Long userId, Long originalPostId, String quote) {
        Post original = getForView(userId, originalPostId);
        Long rootId = (original.getType() == PostType.REPOST && original.getOriginPostId() != null)
                ? original.getOriginPostId() : original.getId();
        Post root = postRepository.findById(rootId).orElse(original);

        Post existing = postRepository.findFirstByAuthorUserIdAndTypeAndOriginPostIdAndStatus(
                userId, PostType.REPOST, root.getId(), PostStatus.PUBLISHED).orElse(null);
        if (existing != null) {
            return existing;
        }
        Post rp = new Post();
        rp.setActorType(ActorType.USER);
        rp.setActorId(userId);
        rp.setAuthorUserId(userId);
        rp.setType(PostType.REPOST);
        rp.setOriginPostId(root.getId());
        rp.setCaption(quote == null || quote.isBlank() ? null : quote.trim());
        rp.setVisibility(PostVisibility.PUBLIC);
        rp.setStatus(PostStatus.PUBLISHED);
        Post saved = postRepository.save(rp);
        postRepository.bumpRepostCount(root.getId(), 1);   // DI-B: +1 ngay trong DB (không đọc-sửa-ghi)
        profileService.adjustPostsCount(userId, 1);
        if (rp.getCaption() != null) {
            hashtagService.linkHashtags(saved.getId(), rp.getCaption());
            mentionService.process(saved.getId(), userId, rp.getCaption());
        }
        notificationService.create(root.getAuthorUserId(), userId, NotificationType.REPOST, root.getId(), null);
        return saved;
    }

    /**
     * Toggle repost trong 1 giao dịch + trả về SỐ ĐẾM MỚI đọc lại từ DB (DI-B).
     * Client dùng số này thay vì tự +1/-1 nên không bao giờ lệch khi bấm nhanh/nhiều tab.
     */
    public RepostResult toggleRepost(Long userId, Long postId) {
        // Tìm bài GỐC rồi KHOÁ nó lại: mọi toggle repost trên cùng bài xếp hàng -> "kiểm tra rồi
        // tạo/gỡ" thành nguyên tử. Thiếu khoá này, N request song song đều thấy "chưa repost"
        // và cùng tạo N bài repost trùng (đã thực nghiệm).
        Long rootId = postId;
        Post p = postRepository.findById(postId).orElse(null);
        if (p != null && p.getType() == PostType.REPOST && p.getOriginPostId() != null) {
            rootId = p.getOriginPostId();
        }
        postRepository.findByIdForUpdate(rootId);   // giữ khoá tới hết giao dịch

        boolean removed = removeRepost(userId, rootId);
        if (!removed) {
            createRepost(userId, rootId, null);
        }
        postRepository.flush();   // đảm bảo UPDATE đếm đã xuống DB trước khi đọc lại
        int count = postRepository.findById(rootId).map(Post::getRepostCount).orElse(0);
        return new RepostResult(!removed, count);
    }

    /** Kết quả toggle repost: trạng thái mới + số lượt đăng lại hiện tại của bài gốc. */
    public record RepostResult(boolean reposted, int count) { }

    /** Gỡ repost của user cho 1 bài gốc. */
    public boolean removeRepost(Long userId, Long originalPostId) {
        Long rootId = originalPostId;
        Post original = postRepository.findById(originalPostId).orElse(null);
        if (original != null && original.getType() == PostType.REPOST && original.getOriginPostId() != null) {
            rootId = original.getOriginPostId();
        }
        Post repost = postRepository.findFirstByAuthorUserIdAndTypeAndOriginPostIdAndStatus(
                userId, PostType.REPOST, rootId, PostStatus.PUBLISHED).orElse(null);
        if (repost == null) {
            return false;
        }
        repost.setStatus(PostStatus.REMOVED);
        repost.setDeletedAt(Instant.now());
        postRepository.save(repost);
        profileService.adjustPostsCount(userId, -1);
        postRepository.bumpRepostCount(rootId, -1);        // DI-B: -1 ngay trong DB, kẹp không âm
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> repostedAmong(Long userId, java.util.Collection<Long> originalPostIds) {
        if (userId == null || originalPostIds == null || originalPostIds.isEmpty()) {
            return java.util.Set.of();
        }
        return postRepository.findByAuthorUserIdAndTypeAndStatusAndOriginPostIdIn(
                        userId, PostType.REPOST, PostStatus.PUBLISHED, originalPostIds)
                .stream().map(Post::getOriginPostId).collect(java.util.stream.Collectors.toSet());
    }

    public void deletePost(Long viewerUserId, boolean isAdmin, Long postId) {
        Post p = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND));
        if (!(isAdmin || p.getAuthorUserId().equals(viewerUserId))) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xoá bài này", HttpStatus.FORBIDDEN);
        }
        p.setStatus(PostStatus.REMOVED);
        p.setDeletedAt(Instant.now());
        postRepository.save(p);
        if (p.getActorType() == ActorType.USER) {
            profileService.adjustPostsCount(p.getAuthorUserId(), -1);
        }
        // Don sach de tranh "rac" + lech bo dem:
        hashtagService.unlinkPost(p.getId());                 // giam Hashtag.postCount + xoa PostHashtag
        if (p.getType() == PostType.REPOST && p.getOriginPostId() != null) {
            postRepository.bumpRepostCount(p.getOriginPostId(), -1);   // DI-B: giam repostCount bai goc (nguyen tu)
        }
    }

    @Transactional(readOnly = true)
    public Post getForView(Long viewerUserId, Long postId) {
        Post p = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND));
        if (!canView(viewerUserId, p)) {
            throw new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND);
        }
        return p;
    }

    @Transactional(readOnly = true)
    public boolean canView(Long viewerUserId, Post p) {
        boolean isAuthor = viewerUserId != null && p.getAuthorUserId().equals(viewerUserId);
        if (p.getStatus() != PostStatus.PUBLISHED) {
            return isAuthor;
        }
        return switch (p.getVisibility()) {
            case PUBLIC -> true;
            case PRIVATE -> isAuthor;
            case FOLLOWERS -> isAuthor
                    || followService.isActiveFollower(viewerUserId, p.getActorType(), p.getActorId());
        };
    }

    @Transactional(readOnly = true)
    public List<Post> postsOfActor(ActorType actorType, Long actorId, long cursor, int size) {
        long cur = cursor > 0 ? cursor : Long.MAX_VALUE;
        return postRepository.findByActorTypeAndActorIdAndStatusAndIdLessThanOrderByIdDesc(
                actorType, actorId, PostStatus.PUBLISHED, cur, PageRequest.of(0, size));
    }

    @Transactional(readOnly = true)
    public long countPostsOfActor(ActorType actorType, Long actorId) {
        return postRepository.countByActorTypeAndActorIdAndStatus(actorType, actorId, PostStatus.PUBLISHED);
    }

    /** Tim kiem bai cong khai theo tu khoa (caption) — cho tim kiem toan cuc. */
    @Transactional(readOnly = true)
    public List<Post> searchPublic(String q, int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return postRepository.searchPublic(PostStatus.PUBLISHED, q.trim(), PageRequest.of(0, limit));
    }

    private static boolean hasMedia(MultipartFile[] files) {
        if (files == null) {
            return false;
        }
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
