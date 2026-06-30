package com.dididi.booking.social.domain.entity;

import com.dididi.booking.common.domain.BaseEntity;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Tha cam xuc (v1: LIKE) len 1 bai hoac 1 binh luan. Moi user chi 1 reaction / doi tuong. */
@Entity
@Table(name = "social_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_reaction_one",
                columnNames = {"user_id", "target_type", "target_id"}),
        indexes = @Index(name = "idx_reaction_target", columnList = "target_type,target_id"))
public class Reaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 8)
    private ReactionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReactionType type = ReactionType.LIKE;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public ReactionTarget getTargetType() { return targetType; }
    public void setTargetType(ReactionTarget targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public ReactionType getType() { return type; }
    public void setType(ReactionType type) { this.type = type; }
}
