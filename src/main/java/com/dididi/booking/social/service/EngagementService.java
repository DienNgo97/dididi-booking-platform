package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.Reaction;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.PostRepository;
import com.dididi.booking.social.repository.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Tha tim (toggle) cho bai/comment + tra ve trang thai da like cua mot lo. */
@Service
@Transactional
public class EngagementService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final PostService postService;

    public EngagementService(ReactionRepository reactionRepository, PostRepository postRepository,
                             CommentRepository commentRepository, NotificationService notificationService,
                             PostService postService) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
        this.postService = postService;
    }

    public LikeResult toggleLike(Long userId, ReactionTarget target, Long targetId) {
        // Chi cho tha tim len bai/binh luan thuoc bai ma nguoi dung duoc phep xem (chong probe + spam notif).
        Long postId = (target == ReactionTarget.POST) ? targetId
                : commentRepository.findById(targetId).map(Comment::getPostId).orElse(null);
        if (postId != null) {
            postService.getForView(userId, postId);
        }
        // DI-B: TOGGLE Ở TẦNG DB (không đọc-rồi-ghi trên entity).
        //   - đang like  -> DELETE, trả về số dòng xoá được;
        //   - chưa like  -> INSERT IGNORE, trùng thì 0 dòng nhưng KHÔNG lỗi (idempotent).
        // Nhờ vậy nhiều request song song không bao giờ làm hỏng transaction (AssertionFailure).
        boolean liked;
        boolean wasLiked = reactionRepository
                .existsByUserIdAndTargetTypeAndTargetId(userId, target, targetId);
        if (wasLiked) {
            reactionRepository.deleteLike(userId, target, targetId);
            liked = false;
        } else {
            reactionRepository.insertIgnoreLike(userId, target.name(), targetId);
            liked = true;
        }
        long count = reactionRepository.countByTargetTypeAndTargetId(target, targetId);
        if (target == ReactionTarget.POST) {
            Post p = postRepository.findById(targetId).orElse(null);
            if (p != null) {
                // DI-B: UPDATE thẳng cột đếm (không save() cả entity -> tránh lost update + ghi đè cột khác)
                postRepository.updateLikeCount(targetId, (int) count);
                if (liked) {
                    notificationService.create(p.getAuthorUserId(), userId, NotificationType.LIKE_POST, targetId, null);
                }
            }
        } else {
            Comment c = commentRepository.findById(targetId).orElse(null);
            if (c != null) {
                commentRepository.updateLikeCount(targetId, (int) count);   // DI-B: UPDATE nguyên tử
                if (liked) {
                    notificationService.create(c.getAuthorUserId(), userId, NotificationType.LIKE_COMMENT, c.getPostId(), targetId);
                }
            }
        }
        return new LikeResult(liked, count);
    }

    @Transactional(readOnly = true)
    public Set<Long> likedPostIds(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return reactionRepository.findByUserIdAndTargetTypeAndTargetIdIn(userId, ReactionTarget.POST, postIds)
                .stream().map(Reaction::getTargetId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> likedCommentIds(Long userId, Collection<Long> commentIds) {
        if (userId == null || commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return reactionRepository.findByUserIdAndTargetTypeAndTargetIdIn(userId, ReactionTarget.COMMENT, commentIds)
                .stream().map(Reaction::getTargetId).collect(Collectors.toSet());
    }

    public record LikeResult(boolean liked, long count) {
    }
}
