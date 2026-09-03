package com.dididi.booking.social.service;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.social.api.dto.ActorView;
import com.dididi.booking.social.api.dto.CommentView;
import com.dididi.booking.social.api.dto.HotelPageView;
import com.dididi.booking.social.api.dto.MediaView;
import com.dididi.booking.social.api.dto.NotificationView;
import com.dididi.booking.social.api.dto.PostView;
import com.dididi.booking.social.api.dto.ProfileView;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Notification;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.PostMedia;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.repository.PostMediaRepository;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Gom entity thanh DTO de hien thi (web + API): tac gia, media, like, bookmark, repost, thong bao. */
@Service
@Transactional(readOnly = true)
public class SocialViewService {

    private final SocialActorService actorService;
    private final PostMediaRepository mediaRepository;
    private final EngagementService engagementService;
    private final HotelRepository hotelRepository;
    private final FollowService followService;
    private final PostService postService;
    private final BookmarkService bookmarkService;
    private final PostRepository postRepository;

    public SocialViewService(SocialActorService actorService, PostMediaRepository mediaRepository,
                             EngagementService engagementService, HotelRepository hotelRepository,
                             FollowService followService, PostService postService,
                             BookmarkService bookmarkService, PostRepository postRepository) {
        this.actorService = actorService;
        this.mediaRepository = mediaRepository;
        this.engagementService = engagementService;
        this.hotelRepository = hotelRepository;
        this.followService = followService;
        this.postService = postService;
        this.bookmarkService = bookmarkService;
        this.postRepository = postRepository;
    }

    public List<PostView> toPostViews(List<Post> posts, Long viewerId, boolean viewerIsAdmin) {
        return buildViews(posts, viewerId, viewerIsAdmin, true);
    }

    private List<PostView> buildViews(List<Post> posts, Long viewerId, boolean viewerIsAdmin, boolean embedReposts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Set<Long> userActorIds = posts.stream().filter(p -> p.getActorType() == ActorType.USER)
                .map(Post::getActorId).collect(Collectors.toSet());
        Set<Long> hotelActorIds = posts.stream().filter(p -> p.getActorType() == ActorType.HOTEL)
                .map(Post::getActorId).collect(Collectors.toSet());
        Map<Long, ActorView> userActors = actorService.batchUserActors(userActorIds);
        Map<Long, ActorView> hotelActors = actorService.batchHotelActors(hotelActorIds);

        Map<Long, List<MediaView>> mediaByPost = new HashMap<>();
        for (PostMedia m : mediaRepository.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds)) {
            mediaByPost.computeIfAbsent(m.getPostId(), k -> new ArrayList<>())
                    .add(new MediaView(m.getId(), m.getMediaType().name(), "/community/media/" + m.getId(), m.getContentType()));
        }

        Set<Long> liked = engagementService.likedPostIds(viewerId, postIds);
        Set<Long> bookmarked = bookmarkService.bookmarkedAmong(viewerId, postIds);
        Set<Long> reposted = postService.repostedAmong(viewerId, postIds);

        Set<Long> tagHotelIds = posts.stream().map(Post::getHotelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> hotelNames = new HashMap<>();
        if (!tagHotelIds.isEmpty()) {
            for (Hotel h : hotelRepository.findAllById(tagHotelIds)) {
                hotelNames.put(h.getId(), h.getName() + (h.getCity() != null ? ", " + h.getCity() : ""));
            }
        }

        Map<Long, PostView> originalViews = new HashMap<>();
        if (embedReposts) {
            Set<Long> originIds = posts.stream()
                    .filter(p -> p.getType() == PostType.REPOST && p.getOriginPostId() != null)
                    .map(Post::getOriginPostId).collect(Collectors.toSet());
            if (!originIds.isEmpty()) {
                for (PostView ov : buildViews(postRepository.findAllById(originIds), viewerId, viewerIsAdmin, false)) {
                    originalViews.put(ov.getId(), ov);
                }
            }
        }

        List<PostView> out = new ArrayList<>(posts.size());
        for (Post p : posts) {
            ActorView actor = p.getActorType() == ActorType.USER
                    ? userActors.getOrDefault(p.getActorId(), actorService.userActor(p.getActorId()))
                    : hotelActors.getOrDefault(p.getActorId(), actorService.hotelActor(p.getActorId()));
            List<MediaView> media = mediaByPost.getOrDefault(p.getId(), List.of());
            String hotelName = p.getHotelId() != null ? hotelNames.get(p.getHotelId()) : null;
            String hotelUrl = p.getHotelId() != null ? "/hotels/" + p.getHotelId() : null;
            boolean canDelete = viewerIsAdmin || (viewerId != null && p.getAuthorUserId().equals(viewerId));
            PostView pv = new PostView(
                    p.getId(), actor, p.getCaption(), linkify(p.getCaption()), p.getType().name(),
                    p.getCreatedAt() != null ? p.getCreatedAt().toEpochMilli() : 0L,
                    media, p.getHotelId(), hotelName, hotelUrl, p.getPlaceName(), p.getLat(), p.getLng(),
                    p.getLikeCount(), p.getCommentCount(), liked.contains(p.getId()), canDelete,
                    "/community/p/" + p.getId());
            pv.setRepostCount(p.getRepostCount());
            pv.setBookmarked(bookmarked.contains(p.getId()));
            pv.setReposted(reposted.contains(p.getId()));
            boolean isRepost = p.getType() == PostType.REPOST;
            pv.setRepost(isRepost);
            if (isRepost && p.getOriginPostId() != null) {
                pv.setOriginal(originalViews.get(p.getOriginPostId()));
            }
            out.add(pv);
        }
        return out;
    }

    public PostView toPostView(Post post, Long viewerId, boolean viewerIsAdmin) {
        List<PostView> v = toPostViews(List.of(post), viewerId, viewerIsAdmin);
        return v.isEmpty() ? null : v.get(0);
    }

    public List<CommentView> toCommentTree(List<Comment> comments, Long viewerId, boolean viewerIsAdmin) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        Set<Long> uids = comments.stream().map(Comment::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, ActorView> actors = actorService.batchUserActors(uids);
        Set<Long> likedC = engagementService.likedCommentIds(viewerId,
                comments.stream().map(Comment::getId).toList());

        Map<Long, List<CommentView>> childMap = new HashMap<>();
        for (Comment c : comments) {
            if (c.getParentId() != null) {
                childMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                        .add(buildComment(c, actors, viewerId, viewerIsAdmin, List.of(), likedC));
            }
        }
        List<CommentView> roots = new ArrayList<>();
        for (Comment c : comments) {
            if (c.getParentId() == null) {
                roots.add(buildComment(c, actors, viewerId, viewerIsAdmin,
                        childMap.getOrDefault(c.getId(), List.of()), likedC));
            }
        }
        return roots;
    }

    public CommentView toCommentView(Comment c, Long viewerId, boolean viewerIsAdmin) {
        Set<Long> likedC = engagementService.likedCommentIds(viewerId, List.of(c.getId()));
        Map<Long, ActorView> actors = Map.of(c.getAuthorUserId(), actorService.userActor(c.getAuthorUserId()));
        return buildComment(c, actors, viewerId, viewerIsAdmin, List.of(), likedC);
    }

    /** Danh sach phang (khong long replies) — dung cho polling binh luan moi. */
    public List<CommentView> toCommentFlat(List<Comment> comments, Long viewerId, boolean viewerIsAdmin) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        Set<Long> uids = comments.stream().map(Comment::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, ActorView> actors = actorService.batchUserActors(uids);
        Set<Long> likedC = engagementService.likedCommentIds(viewerId,
                comments.stream().map(Comment::getId).toList());
        List<CommentView> out = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            out.add(buildComment(c, actors, viewerId, viewerIsAdmin, List.of(), likedC));
        }
        return out;
    }

    private CommentView buildComment(Comment c, Map<Long, ActorView> actors, Long viewerId,
                                     boolean viewerIsAdmin, List<CommentView> replies, Set<Long> likedC) {
        ActorView a = actors.getOrDefault(c.getAuthorUserId(), actorService.userActor(c.getAuthorUserId()));
        boolean canDelete = viewerIsAdmin || (viewerId != null && c.getAuthorUserId().equals(viewerId));
        CommentView cv = new CommentView(c.getId(), a, c.getContent(),
                c.getCreatedAt() != null ? c.getCreatedAt().toEpochMilli() : 0L,
                c.getParentId(), canDelete, replies);
        cv.setLiked(likedC.contains(c.getId()));
        cv.setLikeCount(c.getLikeCount());
        cv.setPostId(c.getPostId());
        return cv;
    }

    public ProfileView profileView(SocialProfile target, Long viewerId) {
        boolean owner = viewerId != null && viewerId.equals(target.getUserId());
        String followState = followService.state(viewerId, ActorType.USER, target.getUserId());
        long followers = followService.followersCount(ActorType.USER, target.getUserId());
        boolean canViewPosts = owner || !target.isPrivate() || "ACTIVE".equals(followState);
        String avatarUrl = SocialProfileService.avatarUrl(target.getUserId(), target.getAvatarKey());
        String coverUrl = SocialProfileService.coverUrl(target.getUserId(), target.getCoverKey());
        return new ProfileView(target.getUserId(), target.getHandle(), target.getDisplayName(), target.getBio(),
                avatarUrl, coverUrl, target.getLink(), target.isPrivate(), target.getPostsCount(),
                followers, target.getFollowingCount(), owner, followState, canViewPosts);
    }

    public HotelPageView hotelPageView(Hotel hotel, Long viewerId, boolean viewerIsAdmin) {
        long posts = postService.countPostsOfActor(ActorType.HOTEL, hotel.getId());
        long followers = followService.followersCount(ActorType.HOTEL, hotel.getId());
        String followState = followService.state(viewerId, ActorType.HOTEL, hotel.getId());
        boolean canPost = viewerIsAdmin || (viewerId != null && viewerId.equals(hotel.getVendorId()));
        return new HotelPageView(hotel.getId(), hotel.getName(), hotel.getCity(), hotel.getStarRating(),
                null, posts, followers, followState, canPost, "/hotels/" + hotel.getId());
    }

    public List<NotificationView> toNotificationViews(List<Notification> notes) {
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        Set<Long> actorIds = notes.stream().map(Notification::getActorUserId).collect(Collectors.toSet());
        Map<Long, ActorView> actors = actorService.batchUserActors(actorIds);
        List<NotificationView> out = new ArrayList<>(notes.size());
        for (Notification n : notes) {
            ActorView a = actors.getOrDefault(n.getActorUserId(), actorService.userActor(n.getActorUserId()));
            String url;
            if (n.getPostId() != null) {
                url = "/community/p/" + n.getPostId();
            } else if (n.getType() == NotificationType.FOLLOW_REQUEST) {
                url = "/community/requests";
            } else {
                url = a.getProfileUrl();
            }
            out.add(new NotificationView(n.getId(), a, n.getType().name(), messageFor(n.getType()), url,
                    n.isRead(), n.getCreatedAt() != null ? n.getCreatedAt().toEpochMilli() : 0L));
        }
        return out;
    }

    /** Phien ban anh (cache-bust): doi khi key doi -> trinh duyet tai lai anh moi. */

    private static String messageFor(NotificationType t) {
        return switch (t) {
            case LIKE_POST -> "đã thích bài viết của bạn";
            case LIKE_COMMENT -> "đã thích bình luận của bạn";
            case COMMENT -> "đã bình luận bài viết của bạn";
            case REPLY -> "đã trả lời bình luận của bạn";
            case FOLLOW -> "đã theo dõi bạn";
            case FOLLOW_REQUEST -> "đã gửi yêu cầu theo dõi bạn";
            case FOLLOW_ACCEPTED -> "đã chấp nhận yêu cầu theo dõi của bạn";
            case MENTION -> "đã nhắc đến bạn";
            case REPOST -> "đã đăng lại bài viết của bạn";
        };
    }

    // ---- linkify caption: escape truoc, roi #hashtag / @mention thanh LINK ----
    private static String linkify(String caption) {
        if (caption == null) {
            return "";
        }
        String esc = htmlEscape(caption);
        esc = esc.replaceAll("#([\\p{L}0-9_]+)",
                "<a class=\"sx-tag\" href=\"/community/tag/$1\">#$1</a>");
        esc = esc.replaceAll("@([A-Za-z0-9_]+)",
                "<a class=\"sx-mention\" href=\"/community/u/$1\">@$1</a>");
        return esc.replace("\n", "<br>");
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
