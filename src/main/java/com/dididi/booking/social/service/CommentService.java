package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Binh luan + tra loi (1 cap). */
@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final MentionService mentionService;
    private final PostService postService;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository,
                          NotificationService notificationService, MentionService mentionService,
                          PostService postService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
        this.mentionService = mentionService;
        this.postService = postService;
    }

    public Comment add(Long userId, Long postId, Long parentId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("EMPTY_COMMENT", "Bình luận không được để trống", HttpStatus.BAD_REQUEST);
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND);
        }
        if (!postService.canView(userId, post)) {
            throw new BusinessException("POST_NOT_FOUND", "Không tìm thấy bài viết", HttpStatus.NOT_FOUND);
        }
        Long normalizedParent = null;
        Long parentAuthorId = null;
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Không tìm thấy bình luận", HttpStatus.NOT_FOUND));
            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException("BAD_PARENT", "Bình luận cha không hợp lệ", HttpStatus.BAD_REQUEST);
            }
            // gioi han reply 1 cap: neu cha la reply thi gan vao goc cua no
            normalizedParent = parent.getParentId() != null ? parent.getParentId() : parent.getId();
            parentAuthorId = parent.getAuthorUserId();
        }
        Comment c = new Comment();
        c.setPostId(postId);
        c.setAuthorUserId(userId);
        c.setParentId(normalizedParent);
        c.setContent(content.trim());
        c.setStatus(PostStatus.PUBLISHED);
        Comment saved = commentRepository.save(c);
        post.setCommentCount((int) commentRepository.countByPostIdAndStatus(postId, PostStatus.PUBLISHED));
        postRepository.save(post);

        // thong bao: tra loi -> bao tac gia binh luan cha; binh luan -> bao tac gia bai
        if (normalizedParent != null && parentAuthorId != null) {
            notificationService.create(parentAuthorId, userId, NotificationType.REPLY, postId, saved.getId());
        } else {
            notificationService.create(post.getAuthorUserId(), userId, NotificationType.COMMENT, postId, saved.getId());
        }
        mentionService.process(postId, userId, content);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Comment> listForPost(Long postId) {
        return commentRepository.findByPostIdAndStatusOrderByIdAsc(postId, PostStatus.PUBLISHED);
    }

    /** Binh luan moi hon afterId (polling realtime). afterId null/<=0 -> tat ca. */
    @Transactional(readOnly = true)
    public List<Comment> newComments(Long postId, Long afterId) {
        return commentRepository.findByPostIdAndStatusAndIdGreaterThanOrderByIdAsc(
                postId, PostStatus.PUBLISHED, afterId == null ? 0L : afterId);
    }

    public void delete(Long userId, boolean isAdmin, Long commentId) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND", "Không tìm thấy bình luận", HttpStatus.NOT_FOUND));
        if (!(isAdmin || c.getAuthorUserId().equals(userId))) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xoá bình luận", HttpStatus.FORBIDDEN);
        }
        c.setStatus(PostStatus.REMOVED);
        c.setDeletedAt(Instant.now());
        commentRepository.save(c);
        postRepository.findById(c.getPostId()).ifPresent(p -> {
            p.setCommentCount((int) commentRepository.countByPostIdAndStatus(p.getId(), PostStatus.PUBLISHED));
            postRepository.save(p);
        });
    }
}
