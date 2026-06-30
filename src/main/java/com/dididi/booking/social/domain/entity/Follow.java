package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.FollowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Quan he theo doi: 1 user theo doi 1 chu the (user khac hoac trang khach san).
 * status = PENDING khi chu the la tai khoan rieng tu can duyet.
 */
@Entity
@Table(name = "social_follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_follow_edge",
                columnNames = {"follower_user_id", "followee_type", "followee_id"}),
        indexes = {
                @Index(name = "idx_follow_follower", columnList = "follower_user_id,status"),
                @Index(name = "idx_follow_followee", columnList = "followee_type,followee_id,status")
        })
public class Follow extends BaseEntity {

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "followee_type", nullable = false, length = 8)
    private ActorType followeeType;

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FollowStatus status = FollowStatus.ACTIVE;

    public Long getFollowerUserId() { return followerUserId; }
    public void setFollowerUserId(Long followerUserId) { this.followerUserId = followerUserId; }
    public ActorType getFolloweeType() { return followeeType; }
    public void setFolloweeType(ActorType followeeType) { this.followeeType = followeeType; }
    public Long getFolloweeId() { return followeeId; }
    public void setFolloweeId(Long followeeId) { this.followeeId = followeeId; }
    public FollowStatus getStatus() { return status; }
    public void setStatus(FollowStatus status) { this.status = status; }
}
