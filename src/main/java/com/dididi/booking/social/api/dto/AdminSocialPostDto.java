package com.dididi.booking.social.api.dto;

import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;

import java.time.Instant;

/** Bài viết cộng đồng cho màn quản lý admin. */
public record AdminSocialPostDto(
        Long id,
        Long authorUserId,
        String authorName,
        String caption,
        PostType type,
        PostStatus status,
        PostVisibility visibility,
        int likeCount,
        int commentCount,
        int repostCount,
        Long hotelId,
        Instant createdAt) {

    public static AdminSocialPostDto from(Post p, String authorName) {
        return new AdminSocialPostDto(
                p.getId(), p.getAuthorUserId(), authorName, p.getCaption(),
                p.getType(), p.getStatus(), p.getVisibility(),
                p.getLikeCount(), p.getCommentCount(), p.getRepostCount(),
                p.getHotelId(), p.getCreatedAt());
    }
}
