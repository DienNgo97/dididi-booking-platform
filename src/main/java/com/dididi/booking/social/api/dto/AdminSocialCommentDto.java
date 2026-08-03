package com.dididi.booking.social.api.dto;

import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.enums.PostStatus;

import java.time.Instant;

/** Bình luận cộng đồng cho màn quản lý admin. */
public record AdminSocialCommentDto(
        Long id,
        Long postId,
        Long authorUserId,
        String authorName,
        String content,
        PostStatus status,
        int likeCount,
        Instant createdAt) {

    public static AdminSocialCommentDto from(Comment c, String authorName) {
        return new AdminSocialCommentDto(
                c.getId(), c.getPostId(), c.getAuthorUserId(), authorName,
                c.getContent(), c.getStatus(), c.getLikeCount(), c.getCreatedAt());
    }
}
