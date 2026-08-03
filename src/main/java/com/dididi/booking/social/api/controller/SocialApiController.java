package com.dididi.booking.social.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.social.api.dto.CommentView;
import com.dididi.booking.social.api.dto.NotificationView;
import com.dididi.booking.social.api.dto.PostView;
import com.dididi.booking.social.api.dto.ProfileView;
import com.dididi.booking.social.api.dto.SocialFeedPage;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.entity.Hashtag;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.FollowStatus;
import com.dididi.booking.social.domain.enums.PostVisibility;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportReason;
import com.dididi.booking.social.service.BookmarkService;
import com.dididi.booking.social.service.CommentService;
import com.dididi.booking.social.service.EngagementService;
import com.dididi.booking.social.service.ExploreService;
import com.dididi.booking.social.service.FeedService;
import com.dididi.booking.social.service.FollowService;
import com.dididi.booking.social.service.HashtagService;
import com.dididi.booking.social.service.NotificationService;
import com.dididi.booking.social.service.PostService;
import com.dididi.booking.social.service.ReportService;
import com.dididi.booking.social.service.SocialMediaService;
import com.dididi.booking.social.service.SocialProfileService;
import com.dididi.booking.social.service.SocialViewService;
import com.dididi.booking.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** REST API mang xa hoi cho Flutter/Angular. JWT: principal name = userId. */
@Tag(name = "Social", description = "Mang xa hoi Dididi (cong dong du lich)")
@RestController
@RequestMapping("/api/v1/social")
public class SocialApiController {

    private static final int PAGE = 12;

    private final FeedService feedService;
    private final PostService postService;
    private final CommentService commentService;
    private final EngagementService engagementService;
    private final FollowService followService;
    private final SocialProfileService profileService;
    private final SocialViewService viewService;
    private final SocialMediaService mediaService;
    private final HotelRepository hotelRepository;
    private final BookmarkService bookmarkService;
    private final NotificationService notificationService;
    private final ExploreService exploreService;
    private final HashtagService hashtagService;
    private final ReportService reportService;

    public SocialApiController(FeedService feedService, PostService postService, CommentService commentService,
                              EngagementService engagementService, FollowService followService,
                              SocialProfileService profileService, SocialViewService viewService,
                              SocialMediaService mediaService, HotelRepository hotelRepository,
                              BookmarkService bookmarkService, NotificationService notificationService,
                              ExploreService exploreService, HashtagService hashtagService,
                              ReportService reportService) {
        this.feedService = feedService;
        this.postService = postService;
        this.commentService = commentService;
        this.engagementService = engagementService;
        this.followService = followService;
        this.profileService = profileService;
        this.viewService = viewService;
        this.mediaService = mediaService;
        this.hotelRepository = hotelRepository;
        this.bookmarkService = bookmarkService;
        this.notificationService = notificationService;
        this.exploreService = exploreService;
        this.hashtagService = hashtagService;
        this.reportService = reportService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    private boolean isAdmin(Authentication auth) {
        return RoleUtils.hasRole(auth, "ADMIN") || RoleUtils.hasRole(auth, "SUPER_ADMIN");
    }

    private SocialFeedPage page(List<Post> posts, Long uid, boolean admin) {
        Long next = posts.size() == PAGE ? posts.get(posts.size() - 1).getId() : null;
        return new SocialFeedPage(viewService.toPostViews(posts, uid, admin), next);
    }

    @Operation(summary = "Feed cá nhân hoá")
    @GetMapping("/feed")
    public ApiResponse<SocialFeedPage> feed(@RequestParam(defaultValue = "0") long cursor, Authentication auth) {
        Long uid = uid(auth);
        profileService.getOrCreate(uid);
        return ApiResponse.ok(page(feedService.feed(uid, cursor, PAGE), uid, isAdmin(auth)));
    }

    @Operation(summary = "Khám phá (bài công khai mới nhất)")
    @GetMapping("/explore")
    public ApiResponse<SocialFeedPage> explore(@RequestParam(defaultValue = "0") long cursor, Authentication auth) {
        return ApiResponse.ok(page(feedService.explore(cursor, PAGE), uid(auth), isAdmin(auth)));
    }

    @Operation(summary = "Đăng bài (ảnh/video, gắn khách sạn). postAs = self | hotel:{id}")
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PostView> create(@RequestParam(required = false) String caption,
                                        @RequestPart(value = "files", required = false) MultipartFile[] files,
                                        @RequestParam(defaultValue = "PUBLIC") String visibility,
                                        @RequestParam(defaultValue = "self") String postAs,
                                        @RequestParam(required = false) String hotelKeyword,
                                        @RequestParam(defaultValue = "false") boolean checkin,
                                        Authentication auth) {
        Long uid = uid(auth);
        ActorType actorType = ActorType.USER;
        Long actorId = uid;
        if (postAs != null && postAs.startsWith("hotel:")) {
            actorType = ActorType.HOTEL;
            actorId = Long.valueOf(postAs.substring("hotel:".length()));
        }
        Long hotelId = null;
        if (hotelKeyword != null && !hotelKeyword.isBlank()) {
            hotelId = hotelRepository.searchActiveByKeyword(hotelKeyword.trim()).stream()
                    .findFirst().map(Hotel::getId).orElse(null);
        }
        if (actorType == ActorType.HOTEL && hotelId == null) {
            hotelId = actorId;
        }
        PostVisibility vis;
        try {
            vis = PostVisibility.valueOf(visibility);
        } catch (IllegalArgumentException e) {
            vis = PostVisibility.PUBLIC;
        }
        Post p = postService.createPost(uid, actorType, actorId, isAdmin(auth), caption, vis, checkin,
                hotelId, null, null, null, null, files);
        return ApiResponse.ok(viewService.toPostView(p, uid, isAdmin(auth)), "Đã đăng bài");
    }

    @Operation(summary = "Khách sạn mà tôi sở hữu (để đăng bài dưới danh nghĩa KS). Rỗng nếu không phải chủ KS.")
    @GetMapping("/my-hotels")
    public ApiResponse<List<Map<String, Object>>> myHotels(Authentication auth) {
        Long uid = uid(auth);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        hotelRepository.findByVendorId(uid).ifPresent(h -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("name", h.getName());
            out.add(m);
        });
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Chi tiết bài")
    @GetMapping("/posts/{id}")
    public ApiResponse<PostView> post(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        return ApiResponse.ok(viewService.toPostView(postService.getForView(uid, id), uid, isAdmin(auth)));
    }

    @Operation(summary = "Xoá bài")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable Long id, Authentication auth) {
        postService.deletePost(uid(auth), isAdmin(auth), id);
        return ApiResponse.ok(null, "Đã xoá");
    }

    @Operation(summary = "Thả/bỏ tim bài (toggle)")
    @PostMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable Long id, Authentication auth) {
        var r = engagementService.toggleLike(uid(auth), ReactionTarget.POST, id);
        return ApiResponse.ok(Map.of("liked", r.liked(), "count", r.count()));
    }

    @Operation(summary = "Danh sách bình luận")
    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<CommentView>> comments(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        return ApiResponse.ok(viewService.toCommentTree(commentService.listForPost(id), uid, isAdmin(auth)));
    }

    @Operation(summary = "Thêm bình luận / trả lời")
    @PostMapping("/posts/{id}/comments")
    public ApiResponse<CommentView> addComment(@PathVariable Long id, @RequestParam String content,
                                               @RequestParam(required = false) Long parentId, Authentication auth) {
        Long uid = uid(auth);
        Comment c = commentService.add(uid, id, parentId, content);
        return ApiResponse.ok(viewService.toCommentView(c, uid, isAdmin(auth)), "Đã bình luận");
    }

    @Operation(summary = "Thả/bỏ tim một bình luận (toggle)")
    @PostMapping("/comments/{id}/like")
    public ApiResponse<Map<String, Object>> likeComment(@PathVariable Long id, Authentication auth) {
        var r = engagementService.toggleLike(uid(auth), ReactionTarget.COMMENT, id);
        return ApiResponse.ok(Map.of("liked", r.liked(), "count", r.count()));
    }

    @Operation(summary = "Theo dõi người dùng")
    @PostMapping("/users/{userId}/follow")
    public ApiResponse<Map<String, Object>> followUser(@PathVariable Long userId, Authentication auth) {
        FollowStatus s = followService.follow(uid(auth), ActorType.USER, userId);
        return ApiResponse.ok(Map.of("state", s.name()));
    }

    @Operation(summary = "Bỏ theo dõi người dùng")
    @DeleteMapping("/users/{userId}/follow")
    public ApiResponse<Void> unfollowUser(@PathVariable Long userId, Authentication auth) {
        followService.unfollow(uid(auth), ActorType.USER, userId);
        return ApiResponse.ok(null, "Đã bỏ theo dõi");
    }

    @Operation(summary = "Theo dõi trang khách sạn")
    @PostMapping("/hotels/{hotelId}/follow")
    public ApiResponse<Map<String, Object>> followHotel(@PathVariable Long hotelId, Authentication auth) {
        FollowStatus s = followService.follow(uid(auth), ActorType.HOTEL, hotelId);
        return ApiResponse.ok(Map.of("state", s.name()));
    }

    @Operation(summary = "Bỏ theo dõi trang khách sạn")
    @DeleteMapping("/hotels/{hotelId}/follow")
    public ApiResponse<Void> unfollowHotel(@PathVariable Long hotelId, Authentication auth) {
        followService.unfollow(uid(auth), ActorType.HOTEL, hotelId);
        return ApiResponse.ok(null, "Đã bỏ theo dõi");
    }

    @Operation(summary = "Hồ sơ của tôi")
    @GetMapping("/me")
    public ApiResponse<ProfileView> me(Authentication auth) {
        Long uid = uid(auth);
        SocialProfile p = profileService.getOrCreate(uid);
        return ApiResponse.ok(viewService.profileView(p, uid));
    }

    @Operation(summary = "Trang cá nhân theo handle")
    @GetMapping("/users/{handle}/profile")
    public ApiResponse<ProfileView> profile(@PathVariable String handle, Authentication auth) {
        Long uid = uid(auth);
        SocialProfile p = profileService.findByHandle(handle)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND", "Không tìm thấy", HttpStatus.NOT_FOUND));
        return ApiResponse.ok(viewService.profileView(p, uid));
    }

    @Operation(summary = "Thông báo của tôi")
    @GetMapping("/notifications")
    public ApiResponse<List<NotificationView>> notifications(Authentication auth) {
        return ApiResponse.ok(viewService.toNotificationViews(notificationService.list(uid(auth), 50)));
    }

    @Operation(summary = "Số thông báo chưa đọc")
    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(Authentication auth) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(uid(auth))));
    }

    @Operation(summary = "Đánh dấu đã đọc tất cả thông báo")
    @PostMapping("/notifications/read")
    public ApiResponse<Void> readAll(Authentication auth) {
        notificationService.markAllRead(uid(auth));
        return ApiResponse.ok(null, "OK");
    }

    @Operation(summary = "Lưu/bỏ lưu bài (toggle)")
    @PostMapping("/posts/{id}/bookmark")
    public ApiResponse<Map<String, Object>> bookmark(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(Map.of("bookmarked", bookmarkService.toggle(uid(auth), id)));
    }

    @Operation(summary = "Đăng lại/bỏ đăng lại (toggle)")
    @PostMapping("/posts/{id}/repost")
    public ApiResponse<Map<String, Object>> repost(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        boolean removed = postService.removeRepost(uid, id);
        if (!removed) {
            postService.createRepost(uid, id, null);
        }
        return ApiResponse.ok(Map.of("reposted", !removed));
    }

    @Operation(summary = "Bài trending")
    @GetMapping("/explore/trending")
    public ApiResponse<List<PostView>> trending(Authentication auth) {
        return ApiResponse.ok(viewService.toPostViews(exploreService.trendingPosts(30), uid(auth), isAdmin(auth)));
    }

    @Operation(summary = "Hashtag thịnh hành")
    @GetMapping("/hashtags/trending")
    public ApiResponse<List<Hashtag>> trendingHashtags() {
        return ApiResponse.ok(exploreService.trendingHashtags());
    }

    @Operation(summary = "Bài theo hashtag")
    @GetMapping("/hashtags/{tag}/posts")
    public ApiResponse<SocialFeedPage> hashtagPosts(@PathVariable String tag,
                                                    @RequestParam(defaultValue = "0") long cursor, Authentication auth) {
        Hashtag h = hashtagService.byTag(tag).orElse(null);
        List<Post> posts = h != null ? hashtagService.postsByTag(h.getId(), cursor, PAGE) : List.of();
        return ApiResponse.ok(page(posts, uid(auth), isAdmin(auth)));
    }

    @Operation(summary = "Báo cáo nội dung (type=POST|COMMENT)")
    @PostMapping("/reports")
    public ApiResponse<Void> report(@RequestParam String type, @RequestParam Long id, @RequestParam String reason,
                                    @RequestParam(required = false) String note, Authentication auth) {
        ReactionTarget tt;
        try {
            tt = ReactionTarget.valueOf(type);
        } catch (IllegalArgumentException e) {
            tt = ReactionTarget.POST;
        }
        ReportReason rr;
        try {
            rr = ReportReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            rr = ReportReason.OTHER;
        }
        reportService.submit(uid(auth), tt, id, rr, note);
        return ApiResponse.ok(null, "Đã gửi báo cáo");
    }

    // ---------- Parity mobile: trang KS, yêu cầu theo dõi, avatar/bìa/handle, xoá bình luận ----------

    @Operation(summary = "Bài viết của trang khách sạn (cộng đồng theo KS)")
    @GetMapping("/hotels/{hotelId}/posts")
    public ApiResponse<SocialFeedPage> hotelPosts(@PathVariable Long hotelId,
                                                  @RequestParam(defaultValue = "0") long cursor, Authentication auth) {
        return ApiResponse.ok(page(postService.postsOfActor(ActorType.HOTEL, hotelId, cursor, PAGE),
                uid(auth), isAdmin(auth)));
    }

    @Operation(summary = "Bài viết của một người dùng (để hiển thị trên trang cá nhân)")
    @GetMapping("/users/{userId}/posts")
    public ApiResponse<SocialFeedPage> userPosts(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "0") long cursor, Authentication auth) {
        return ApiResponse.ok(page(postService.postsOfActor(ActorType.USER, userId, cursor, PAGE),
                uid(auth), isAdmin(auth)));
    }

    @Operation(summary = "Danh sách yêu cầu theo dõi đang chờ (tài khoản riêng tư)")
    @GetMapping("/follow-requests")
    public ApiResponse<List<Map<String, Object>>> followRequests(Authentication auth) {
        Long uid = uid(auth);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Follow f : followService.pendingRequestsFor(uid)) {
            SocialProfile prof = profileService.getOrCreate(f.getFollowerUserId());
            out.add(Map.of("followId", f.getId(), "profile", viewService.profileView(prof, uid)));
        }
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Chấp nhận yêu cầu theo dõi")
    @PostMapping("/follow-requests/{followId}/accept")
    public ApiResponse<Void> acceptFollowRequest(@PathVariable Long followId, Authentication auth) {
        followService.acceptRequest(uid(auth), followId);
        return ApiResponse.ok(null, "Đã chấp nhận");
    }

    @Operation(summary = "Từ chối yêu cầu theo dõi")
    @PostMapping("/follow-requests/{followId}/reject")
    public ApiResponse<Void> rejectFollowRequest(@PathVariable Long followId, Authentication auth) {
        followService.rejectRequest(uid(auth), followId);
        return ApiResponse.ok(null, "Đã từ chối");
    }

    @Operation(summary = "Cập nhật ảnh đại diện")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> setAvatar(@RequestPart("image") MultipartFile file, Authentication auth) {
        profileService.setAvatarKey(uid(auth), mediaService.uploadAvatar(file));
        return ApiResponse.ok(null, "Đã cập nhật ảnh đại diện");
    }

    @Operation(summary = "Cập nhật ảnh bìa")
    @PostMapping(value = "/me/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> setCover(@RequestPart("image") MultipartFile file, Authentication auth) {
        profileService.setCoverKey(uid(auth), mediaService.uploadAvatar(file));
        return ApiResponse.ok(null, "Đã cập nhật ảnh bìa");
    }

    @Operation(summary = "Đổi tên người dùng (@handle)")
    @PostMapping("/me/handle")
    public ApiResponse<ProfileView> changeHandle(@RequestParam String handle, Authentication auth) {
        Long uid = uid(auth);
        SocialProfile p = profileService.changeHandle(uid, handle);
        return ApiResponse.ok(viewService.profileView(p, uid), "Đã đổi tên người dùng");
    }

    @Operation(summary = "Xoá bình luận của mình")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id, Authentication auth) {
        commentService.delete(uid(auth), isAdmin(auth), id);
        return ApiResponse.ok(null, "Đã xoá");
    }

    @Operation(summary = "Tải media (ảnh/video) của bài")
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> media(@PathVariable Long id, Authentication auth) {
        // Yeu cau dang nhap + chi serve media cua bai duoc phep xem (chong IDOR).
        postService.getForView(uid(auth), mediaService.postIdOf(id));
        StorageService.StoredObject obj = mediaService.loadMedia(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.safeContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(obj.bytes());
    }
}
