package com.dididi.booking.social.api.dto;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.social.domain.entity.SocialProfile;

import java.time.Instant;

/** Thành viên cộng đồng (hồ sơ social + trạng thái tài khoản) cho màn quản lý admin. */
public record AdminSocialMemberDto(
        Long userId,
        String handle,
        String displayName,
        String fullName,
        String email,
        Role role,
        UserStatus accountStatus,
        int postsCount,
        int followersCount,
        int followingCount,
        Instant joinedAt) {

    public static AdminSocialMemberDto from(SocialProfile p, User u) {
        return new AdminSocialMemberDto(
                p.getUserId(), p.getHandle(), p.getDisplayName(),
                u != null ? u.getFullName() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getRole() : null,
                u != null ? u.getStatus() : null,
                p.getPostsCount(), p.getFollowersCount(), p.getFollowingCount(),
                p.getCreatedAt());
    }
}
