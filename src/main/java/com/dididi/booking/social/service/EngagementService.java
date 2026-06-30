package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.Reaction;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReactionType;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.PostRepository;
import com.dididi.booking.social.repository.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
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
        Optional<Reaction> existing = reactionRepository
                .findByUserIdAndTargetTypeAndTargetId(userId, target, targetId);
        boolean liked;
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            liked = false;
        } else {
            Reaction r = new Reaction();
            r.setUserId(userId);
            r.setTargetType(target);
            r.setTargetId(targetId);
            r.setType(ReactionType.LIKE);
            reactionRepository.save(r);
            liked = true;
        }
        long count = reactionRepository.countByTargetTypeAndTargetId(target, targetId);
        if (target == ReactionTarget.POST) {
            Post p = postRepository.findById(targetId).orElse(null);
            if (p != null) {
                p.setLikeCount((int) count);
                postRepository.save(p);
                if (liked) {
                    notificationService.create(p.getAuthorUserId(), userId, NotificationType.LIKE_POST, targetId, null);
                }
            }
        } else {
            Comment c = commentRepository.findById(targetId).orElse(null);
            if (c != null) {
                c.setLikeCount((int) count);
                commentRepository.save(c);
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
