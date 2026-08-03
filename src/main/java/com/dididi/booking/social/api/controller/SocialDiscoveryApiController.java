package com.dididi.booking.social.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.social.api.dto.PostView;
import com.dididi.booking.social.api.dto.ProfileView;
import com.dididi.booking.social.api.dto.UserCardView;
import com.dididi.booking.social.domain.entity.Hashtag;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.service.BookmarkService;
import com.dididi.booking.social.service.HashtagService;
import com.dididi.booking.social.service.PostService;
import com.dididi.booking.social.service.SocialDiscoveryService;
import com.dididi.booking.social.service.SocialProfileService;
import com.dididi.booking.social.service.SocialViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Khám phá mạng xã hội cho khách: tìm kiếm, gợi ý người, bài đã lưu, cập nhật hồ sơ/riêng tư.
 * Dùng lại các service sẵn có (SocialDiscoveryService, PostService, HashtagService, BookmarkService, SocialProfileService).
 */
@Tag(name = "Social discovery (khách)")
@RestController
@RequestMapping("/api/v1/social")
public class SocialDiscoveryApiController {

    private final SocialDiscoveryService discoveryService;
    private final PostService postService;
    private final HashtagService hashtagService;
    private final BookmarkService bookmarkService;
    private final SocialProfileService profileService;
    private final SocialViewService viewService;

    public SocialDiscoveryApiController(SocialDiscoveryService discoveryService, PostService postService,
                                        HashtagService hashtagService, BookmarkService bookmarkService,
                                        SocialProfileService profileService, SocialViewService viewService) {
        this.discoveryService = discoveryService;
        this.postService = postService;
        this.hashtagService = hashtagService;
        this.bookmarkService = bookmarkService;
        this.profileService = profileService;
        this.viewService = viewService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Tìm kiếm: người dùng + bài viết + hashtag")
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(required = false) String q, Authentication auth) {
        Long me = uid(auth);
        List<UserCardView> users = discoveryService.search(me, q, 10);
        List<PostView> posts = (q == null || q.isBlank())
                ? List.of()
                : viewService.toPostViews(postService.searchPublic(q, 10), me, false);
        List<Hashtag> hashtags = (q == null || q.isBlank()) ? List.of() : hashtagService.search(q);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", users);
        out.put("posts", posts);
        out.put("hashtags", hashtags);
        return ApiResponse.ok(out);
    }

    @Operation(summary = "Gợi ý người để theo dõi")
    @GetMapping("/people")
    public ApiResponse<List<UserCardView>> people(Authentication auth) {
        return ApiResponse.ok(discoveryService.suggest(uid(auth), 20));
    }

    @Operation(summary = "Bài viết đã lưu của tôi")
    @GetMapping("/bookmarks")
    public ApiResponse<List<PostView>> bookmarks(Authentication auth) {
        Long me = uid(auth);
        List<Post> posts = new ArrayList<>();
        for (Long id : bookmarkService.postIds(me, 50)) {
            try {
                posts.add(postService.getForView(me, id));
            } catch (Exception ignored) {
                // bài đã xoá/không xem được -> bỏ qua
            }
        }
        return ApiResponse.ok(viewService.toPostViews(posts, me, false));
    }

    @Operation(summary = "Cập nhật hồ sơ mạng xã hội + quyền riêng tư (private=true: tài khoản riêng tư)")
    @PostMapping("/me")
    public ApiResponse<ProfileView> updateMe(@RequestBody Map<String, Object> body, Authentication auth) {
        Long me = uid(auth);
        SocialProfile cur = profileService.getOrCreate(me);
        String displayName = body.get("displayName") == null ? cur.getDisplayName() : body.get("displayName").toString();
        String bio = body.get("bio") == null ? cur.getBio() : body.get("bio").toString();
        String link = body.get("link") == null ? cur.getLink() : body.get("link").toString();
        boolean isPrivate = body.get("private") == null
                ? "PRIVATE".equalsIgnoreCase(String.valueOf(cur.getVisibility()))
                : Boolean.parseBoolean(String.valueOf(body.get("private")));
        SocialProfile p = profileService.updateProfile(me, displayName, bio, link, isPrivate);
        return ApiResponse.ok(viewService.profileView(p, me), "Đã cập nhật hồ sơ");
    }
}
