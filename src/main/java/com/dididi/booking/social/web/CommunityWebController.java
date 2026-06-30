package com.dididi.booking.social.web;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.social.api.dto.ActorView;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Follow;
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
import com.dididi.booking.social.service.SocialActorService;
import com.dididi.booking.social.service.SocialDiscoveryService;
import com.dididi.booking.social.service.SocialMediaService;
import com.dididi.booking.social.service.SocialProfileService;
import com.dididi.booking.social.service.SocialViewService;
import com.dididi.booking.storage.StorageService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Web khach (Thymeleaf SSR + AJAX) cho tab Cong dong. */
@Controller
public class CommunityWebController {

    private static final int PAGE = 12;

    private final CurrentUser currentUser;
    private final FeedService feedService;
    private final PostService postService;
    private final CommentService commentService;
    private final EngagementService engagementService;
    private final FollowService followService;
    private final SocialProfileService profileService;
    private final SocialViewService viewService;
    private final SocialActorService actorService;
    private final SocialMediaService mediaService;
    private final HotelRepository hotelRepository;
    private final BookmarkService bookmarkService;
    private final NotificationService notificationService;
    private final ExploreService exploreService;
    private final HashtagService hashtagService;
    private final ReportService reportService;
    private final SocialDiscoveryService discoveryService;

    public CommunityWebController(CurrentUser currentUser, FeedService feedService, PostService postService,
                                  CommentService commentService, EngagementService engagementService,
                                  FollowService followService, SocialProfileService profileService,
                                  SocialViewService viewService, SocialActorService actorService,
                                  SocialMediaService mediaService, HotelRepository hotelRepository,
                                  BookmarkService bookmarkService, NotificationService notificationService,
                                  ExploreService exploreService, HashtagService hashtagService,
                                  ReportService reportService, SocialDiscoveryService discoveryService) {
        this.currentUser = currentUser;
        this.feedService = feedService;
        this.postService = postService;
        this.commentService = commentService;
        this.engagementService = engagementService;
        this.followService = followService;
        this.profileService = profileService;
        this.viewService = viewService;
        this.actorService = actorService;
        this.mediaService = mediaService;
        this.hotelRepository = hotelRepository;
        this.bookmarkService = bookmarkService;
        this.notificationService = notificationService;
        this.exploreService = exploreService;
        this.hashtagService = hashtagService;
        this.reportService = reportService;
        this.discoveryService = discoveryService;
    }

    private boolean isAdmin(Authentication auth) {
        return RoleUtils.hasRole(auth, "ADMIN") || RoleUtils.hasRole(auth, "SUPER_ADMIN");
    }

    // ===================== FEED =====================

    @GetMapping("/community")
    public String feed(Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        if (uid == null) {
            return "redirect:/community/explore";   // khách vãng lai -> trang Khám phá công khai
        }
        profileService.getOrCreate(uid);
        List<Post> posts = feedService.feed(uid, 0, PAGE);
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        model.addAttribute("moreUrl", "/community/more/feed");
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("pendingCount", followService.pendingRequestsFor(uid).size());
        model.addAttribute("activeTab", "feed");
        addCommunitySidebar(model, uid);
        return "social/feed";
    }

    @GetMapping("/community/explore")
    public String explore(Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        List<Post> posts = exploreService.trendingPosts(30);
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("trendingTags", exploreService.trendingHashtags());
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        model.addAttribute("activeTab", "explore");
        addCommunitySidebar(model, uid);
        return "social/explore";
    }

    @GetMapping("/community/more/feed")
    public String moreFeed(@RequestParam(defaultValue = "0") long cursor, Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        List<Post> posts = feedService.feed(uid, cursor, PAGE);
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        return "social/fragments :: cards";
    }

    @GetMapping("/community/more/explore")
    public String moreExplore(@RequestParam(defaultValue = "0") long cursor, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        List<Post> posts = feedService.explore(cursor, PAGE);
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        return "social/fragments :: cards";
    }

    // ===================== PEOPLE (tim kiem / goi y ket noi) =====================

    @GetMapping("/community/people")
    public String people(@RequestParam(required = false) String q, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        if (uid != null) {
            profileService.getOrCreate(uid);
        }
        model.addAttribute("people", discoveryService.search(uid, q, 24));
        model.addAttribute("query", q == null ? "" : q);
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        model.addAttribute("activeTab", "people");
        addCommunitySidebar(model, uid);
        return "social/people";
    }

    @GetMapping("/community/people/search")
    public String peopleSearch(@RequestParam(required = false) String q, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        model.addAttribute("people", discoveryService.search(uid, q, 24));
        return "social/people :: results";
    }

    // ===================== TÌM KIẾM TOÀN CỤC (user + bài viết + hashtag) =====================

    @GetMapping("/community/search")
    public String search(@RequestParam(required = false) String q, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        if (uid != null) {
            profileService.getOrCreate(uid);
        }
        String query = (q == null) ? "" : q.trim();
        model.addAttribute("query", query);
        if (!query.isBlank()) {
            model.addAttribute("people", discoveryService.search(uid, query, 12));
            model.addAttribute("posts", viewService.toPostViews(postService.searchPublic(query, 12), uid, isAdmin(auth)));
            model.addAttribute("tags", hashtagService.search(query));
        }
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        model.addAttribute("activeTab", "search");
        addCommunitySidebar(model, uid);
        return "social/search";
    }

    // ===================== PROFILE (user) =====================

    @GetMapping("/community/u/{handle}")
    public String profile(@PathVariable String handle, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        SocialProfile target = profileService.findByHandle(handle)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND", "Không tìm thấy trang cá nhân", HttpStatus.NOT_FOUND));
        var pv = viewService.profileView(target, uid);
        model.addAttribute("profile", pv);
        if (pv.isCanViewPosts()) {
            List<Post> posts = postService.postsOfActor(ActorType.USER, target.getUserId(), 0, PAGE);
            model.addAttribute("posts", viewService.toPostViews(visibleOnly(posts, uid), uid, isAdmin(auth)));
            model.addAttribute("nextCursor", nextCursor(posts));
            model.addAttribute("moreUrl", "/community/more/user/" + handle);
        }
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        addCommunitySidebar(model, uid);
        return "social/profile";
    }

    @GetMapping("/community/more/user/{handle}")
    public String moreUser(@PathVariable String handle, @RequestParam(defaultValue = "0") long cursor,
                           Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        SocialProfile target = profileService.findByHandle(handle)
                .orElseThrow(() -> new BusinessException("PROFILE_NOT_FOUND", "Không tìm thấy", HttpStatus.NOT_FOUND));
        var pv = viewService.profileView(target, uid);
        List<Post> posts = pv.isCanViewPosts()
                ? postService.postsOfActor(ActorType.USER, target.getUserId(), cursor, PAGE) : List.of();
        model.addAttribute("posts", viewService.toPostViews(visibleOnly(posts, uid), uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        return "social/fragments :: cards";
    }

    // ===================== HOTEL PAGE =====================

    @GetMapping("/community/hotel/{hotelId}")
    public String hotelPage(@PathVariable Long hotelId, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
        model.addAttribute("page", viewService.hotelPageView(hotel, uid, isAdmin(auth)));
        List<Post> posts = postService.postsOfActor(ActorType.HOTEL, hotelId, 0, PAGE);
        model.addAttribute("posts", viewService.toPostViews(visibleOnly(posts, uid), uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        model.addAttribute("moreUrl", "/community/more/hotel/" + hotelId);
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        return "social/hotel-page";
    }

    @GetMapping("/community/more/hotel/{hotelId}")
    public String moreHotel(@PathVariable Long hotelId, @RequestParam(defaultValue = "0") long cursor,
                            Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        List<Post> posts = postService.postsOfActor(ActorType.HOTEL, hotelId, cursor, PAGE);
        model.addAttribute("posts", viewService.toPostViews(visibleOnly(posts, uid), uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        return "social/fragments :: cards";
    }

    // ===================== POST DETAIL =====================

    @GetMapping("/community/p/{id}")
    public String postDetail(@PathVariable Long id, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        Post post = postService.getForView(uid, id);
        model.addAttribute("post", viewService.toPostView(post, uid, isAdmin(auth)));
        List<Comment> comments = commentService.listForPost(id);
        model.addAttribute("comments", viewService.toCommentTree(comments, uid, isAdmin(auth)));
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        addCommunitySidebar(model, uid);
        return "social/post";
    }

    // ===================== COMPOSE / CREATE =====================

    @GetMapping("/community/compose")
    public String compose(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("postAsHotels", hotelRepository.findByVendorId(uid).map(List::of).orElse(List.of()));
        model.addAttribute("tagHotels", hotelRepository.findByActiveTrue());
        return "social/compose";
    }

    @PostMapping("/community/posts")
    public String createPost(@RequestParam(required = false) String caption,
                             @RequestParam(required = false) MultipartFile[] files,
                             @RequestParam(defaultValue = "PUBLIC") String visibility,
                             @RequestParam(defaultValue = "self") String postAs,
                             @RequestParam(required = false) String hotelKeyword,
                             @RequestParam(defaultValue = "false") boolean checkin,
                             Authentication auth, RedirectAttributes ra) {
        Long uid = currentUser.id(auth);
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
            hotelId = actorId; // dang duoi danh nghia KS -> tu gan chinh KS do
        }
        PostVisibility vis;
        try {
            vis = PostVisibility.valueOf(visibility);
        } catch (IllegalArgumentException e) {
            vis = PostVisibility.PUBLIC;
        }
        postService.createPost(uid, actorType, actorId, isAdmin(auth), caption, vis, checkin,
                hotelId, null, null, null, null, files);
        ra.addFlashAttribute("message", "Đã đăng bài lên Cộng đồng.");
        return "redirect:/community";
    }

    @PostMapping("/community/posts/{id}/delete")
    public String deletePost(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        postService.deletePost(currentUser.id(auth), isAdmin(auth), id);
        ra.addFlashAttribute("message", "Đã xoá bài viết.");
        return "redirect:/community";
    }

    // ===================== LIKE (AJAX) =====================

    @PostMapping("/community/posts/{id}/like-ajax")
    @ResponseBody
    public Map<String, Object> likePost(@PathVariable Long id, Authentication auth) {
        var r = engagementService.toggleLike(currentUser.id(auth), ReactionTarget.POST, id);
        return Map.of("ok", true, "liked", r.liked(), "count", r.count());
    }

    @PostMapping("/community/comments/{id}/like-ajax")
    @ResponseBody
    public Map<String, Object> likeComment(@PathVariable Long id, Authentication auth) {
        var r = engagementService.toggleLike(currentUser.id(auth), ReactionTarget.COMMENT, id);
        return Map.of("ok", true, "liked", r.liked(), "count", r.count());
    }

    // ===================== COMMENT =====================

    @PostMapping("/community/posts/{postId}/comments")
    public String addComment(@PathVariable Long postId, @RequestParam String content,
                             @RequestParam(required = false) String parentId,
                             Authentication auth, RedirectAttributes ra) {
        Long pid = (parentId != null && !parentId.isBlank()) ? Long.valueOf(parentId.trim()) : null;
        commentService.add(currentUser.id(auth), postId, pid, content);
        return "redirect:/community/p/" + postId;
    }

    /** Them binh luan qua AJAX -> tra ve 1 node de chen khong tai lai trang (giong co che tin nhan). */
    @PostMapping("/community/posts/{postId}/comments-ajax")
    public String addCommentAjax(@PathVariable Long postId, @RequestParam String content,
                                 @RequestParam(required = false) String parentId,
                                 Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        Long pid = (parentId != null && !parentId.isBlank()) ? Long.valueOf(parentId.trim()) : null;
        Comment c = commentService.add(uid, postId, pid, content);
        model.addAttribute("c", viewService.toCommentView(c, uid, isAdmin(auth)));
        return "social/comment-fragments :: node";
    }

    /** Polling binh luan moi (id > afterId) -> tra ve cac node moi. */
    @GetMapping("/community/posts/{postId}/comments/poll")
    public String pollComments(@PathVariable Long postId, @RequestParam(defaultValue = "0") long afterId,
                               Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("comments",
                viewService.toCommentFlat(commentService.newComments(postId, afterId), uid, isAdmin(auth)));
        return "social/comment-fragments :: batch";
    }

    @PostMapping("/community/comments/{id}/delete")
    public String deleteComment(@PathVariable Long id, @RequestParam Long postId,
                                Authentication auth, RedirectAttributes ra) {
        commentService.delete(currentUser.id(auth), isAdmin(auth), id);
        return "redirect:/community/p/" + postId;
    }

    // ===================== FOLLOW (AJAX toggle) =====================

    @PostMapping("/community/follow-ajax")
    @ResponseBody
    public Map<String, Object> followToggle(@RequestParam String followeeType, @RequestParam Long followeeId,
                                            Authentication auth) {
        Long uid = currentUser.id(auth);
        ActorType type = ActorType.valueOf(followeeType);
        String state = followService.state(uid, type, followeeId);
        String newState;
        if ("ACTIVE".equals(state) || "PENDING".equals(state)) {
            followService.unfollow(uid, type, followeeId);
            newState = "NONE";
        } else if ("SELF".equals(state)) {
            newState = "SELF";
        } else {
            FollowStatus s = followService.follow(uid, type, followeeId);
            newState = s == FollowStatus.ACTIVE ? "ACTIVE" : "PENDING";
        }
        return Map.of("ok", true, "state", newState, "followers", followService.followersCount(type, followeeId));
    }

    // ===================== FOLLOW REQUESTS (tai khoan rieng tu) =====================

    @GetMapping("/community/requests")
    public String requests(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        List<Map<String, Object>> reqs = new ArrayList<>();
        for (Follow f : followService.pendingRequestsFor(uid)) {
            reqs.add(Map.of("followId", f.getId(), "actor", actorService.userActor(f.getFollowerUserId())));
        }
        model.addAttribute("requests", reqs);
        model.addAttribute("me", actorService.userActor(uid));
        return "social/requests";
    }

    @PostMapping("/community/requests/{followId}/accept")
    public String acceptRequest(@PathVariable Long followId, Authentication auth, RedirectAttributes ra) {
        followService.acceptRequest(currentUser.id(auth), followId);
        ra.addFlashAttribute("message", "Đã chấp nhận yêu cầu theo dõi.");
        return "redirect:/community/requests";
    }

    @PostMapping("/community/requests/{followId}/reject")
    public String rejectRequest(@PathVariable Long followId, Authentication auth, RedirectAttributes ra) {
        followService.rejectRequest(currentUser.id(auth), followId);
        ra.addFlashAttribute("message", "Đã từ chối yêu cầu.");
        return "redirect:/community/requests";
    }

    // ===================== SETTINGS =====================

    @GetMapping("/community/settings")
    public String settings(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        SocialProfile p = profileService.getOrCreate(uid);
        model.addAttribute("profile", p);
        model.addAttribute("me", actorService.userActor(uid));
        return "social/settings";
    }

    @PostMapping("/community/settings")
    public String saveSettings(@RequestParam(required = false) String displayName,
                               @RequestParam(required = false) String bio,
                               @RequestParam(required = false) String link,
                               @RequestParam(defaultValue = "false") boolean isPrivate,
                               Authentication auth, RedirectAttributes ra) {
        profileService.updateProfile(currentUser.id(auth), displayName, bio, link, isPrivate);
        ra.addFlashAttribute("message", "Đã lưu hồ sơ.");
        return "redirect:/community/settings";
    }

    @PostMapping("/community/settings/handle")
    public String changeHandle(@RequestParam String handle, Authentication auth, RedirectAttributes ra) {
        try {
            profileService.changeHandle(currentUser.id(auth), handle);
            ra.addFlashAttribute("message", "Đã đổi tên định danh.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/community/settings";
    }

    @PostMapping("/community/settings/avatar")
    public String uploadAvatar(@RequestParam MultipartFile file, Authentication auth, RedirectAttributes ra) {
        String key = mediaService.uploadAvatar(file);
        profileService.setAvatarKey(currentUser.id(auth), key);
        ra.addFlashAttribute("message", "Đã cập nhật ảnh đại diện.");
        return "redirect:/community/settings";
    }

    @PostMapping("/community/settings/cover")
    public String uploadCover(@RequestParam MultipartFile file, Authentication auth, RedirectAttributes ra) {
        String key = mediaService.uploadAvatar(file);
        profileService.setCoverKey(currentUser.id(auth), key);
        ra.addFlashAttribute("message", "Đã cập nhật ảnh bìa.");
        return "redirect:/community/settings";
    }

    // ===================== BOOKMARK / REPOST (AJAX) =====================

    @PostMapping("/community/posts/{id}/bookmark-ajax")
    @ResponseBody
    public Map<String, Object> bookmark(@PathVariable Long id, Authentication auth) {
        boolean saved = bookmarkService.toggle(currentUser.id(auth), id);
        return Map.of("ok", true, "bookmarked", saved);
    }

    @PostMapping("/community/posts/{id}/repost-ajax")
    @ResponseBody
    public Map<String, Object> repost(@PathVariable Long id, Authentication auth) {
        Long uid = currentUser.id(auth);
        boolean removed = postService.removeRepost(uid, id);
        if (!removed) {
            postService.createRepost(uid, id, null);
        }
        return Map.of("ok", true, "reposted", !removed);
    }

    // ===================== NOTIFICATIONS =====================

    @GetMapping("/community/notifications")
    public String notifications(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("notifications", viewService.toNotificationViews(notificationService.list(uid, 50)));
        model.addAttribute("me", actorService.userActor(uid));
        notificationService.markAllRead(uid);
        addCommunitySidebar(model, uid);
        return "social/notifications";
    }

    // ===================== BOOKMARKS =====================

    @GetMapping("/community/bookmarks")
    public String bookmarks(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        List<Post> posts = new ArrayList<>();
        for (Long pid : bookmarkService.postIds(uid, 60)) {
            try {
                posts.add(postService.getForView(uid, pid));
            } catch (RuntimeException ignore) {
                // bài đã gỡ / không xem được -> bỏ qua
            }
        }
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("me", actorService.userActor(uid));
        addCommunitySidebar(model, uid);
        return "social/bookmarks";
    }

    // ===================== HASHTAG =====================

    @GetMapping("/community/tag/{tag}")
    public String tag(@PathVariable String tag, Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        var h = hashtagService.byTag(tag).orElse(null);
        model.addAttribute("tag", tag.toLowerCase());
        if (h != null) {
            List<Post> posts = hashtagService.postsByTag(h.getId(), 0, PAGE);
            model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
            model.addAttribute("nextCursor", nextCursor(posts));
            model.addAttribute("moreUrl", "/community/more/tag/" + tag);
            model.addAttribute("postCount", h.getPostCount());
        } else {
            model.addAttribute("posts", List.of());
        }
        if (uid != null) {
            model.addAttribute("me", actorService.userActor(uid));
        }
        addCommunitySidebar(model, uid);
        return "social/tag";
    }

    @GetMapping("/community/more/tag/{tag}")
    public String moreTag(@PathVariable String tag, @RequestParam(defaultValue = "0") long cursor,
                          Authentication auth, Model model) {
        Long uid = currentUser.idOrNull(auth);
        var h = hashtagService.byTag(tag).orElse(null);
        List<Post> posts = h != null ? hashtagService.postsByTag(h.getId(), cursor, PAGE) : List.of();
        model.addAttribute("posts", viewService.toPostViews(posts, uid, isAdmin(auth)));
        model.addAttribute("nextCursor", nextCursor(posts));
        return "social/fragments :: cards";
    }

    // ===================== REPORT =====================

    @GetMapping("/community/report")
    public String reportForm(@RequestParam String type, @RequestParam Long id, Authentication auth, Model model) {
        model.addAttribute("targetType", type);
        model.addAttribute("targetId", id);
        model.addAttribute("me", actorService.userActor(currentUser.id(auth)));
        return "social/report";
    }

    @PostMapping("/community/report")
    public String submitReport(@RequestParam String type, @RequestParam Long id, @RequestParam String reason,
                               @RequestParam(required = false) String note, Authentication auth, RedirectAttributes ra) {
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
        reportService.submit(currentUser.id(auth), tt, id, rr, note);
        ra.addFlashAttribute("message", "Đã gửi báo cáo. Cảm ơn bạn đã giúp giữ Cộng đồng an toàn.");
        return "redirect:/community";
    }

    // ===================== MEDIA SERVE =====================

    @GetMapping("/community/media/{id}")
    public ResponseEntity<byte[]> media(@PathVariable Long id, Authentication auth) {
        // Chi serve media cua bai ma nguoi xem duoc phep xem (chong IDOR bai rieng tu/followers).
        postService.getForView(currentUser.idOrNull(auth), mediaService.postIdOf(id));
        StorageService.StoredObject obj = mediaService.loadMedia(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.safeContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(obj.bytes());
    }

    @GetMapping("/community/avatar/u/{userId}")
    public ResponseEntity<byte[]> avatar(@PathVariable Long userId) {
        return serveProfileImage(userId, true);
    }

    @GetMapping("/community/cover/u/{userId}")
    public ResponseEntity<byte[]> cover(@PathVariable Long userId) {
        return serveProfileImage(userId, false);
    }

    private ResponseEntity<byte[]> serveProfileImage(Long userId, boolean avatar) {
        SocialProfile p = profileService.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND));
        String key = avatar ? p.getAvatarKey() : p.getCoverKey();
        StorageService.StoredObject obj = mediaService.loadByKey(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.safeContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(obj.bytes());
    }

    private static Long nextCursor(List<Post> posts) {
        return (posts.size() == PAGE) ? posts.get(posts.size() - 1).getId() : null;
    }

    /** Loc bo bai ma nguoi xem khong duoc phep xem (FOLLOWERS/PRIVATE cua tai khoan cong khai). */
    private List<Post> visibleOnly(List<Post> posts, Long uid) {
        return posts.stream().filter(p -> postService.canView(uid, p)).toList();
    }

    /** Cap du lieu cho sidebar phai (goi y ket noi + hashtag trending + so yeu cau cho). */
    private void addCommunitySidebar(Model model, Long uid) {
        if (uid != null) {   // khach vang lai: khong goi y ket noi / so yeu cau
            model.addAttribute("sidebarSuggestions", discoveryService.suggest(uid, 5));
            model.addAttribute("pendingCount", followService.pendingRequestsFor(uid).size());
        }
        List<?> tags = exploreService.trendingHashtags();
        model.addAttribute("sidebarTrending",
                (tags != null && tags.size() > 6) ? tags.subList(0, 6) : tags);
    }
}
