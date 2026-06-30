package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.FollowStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerUserIdAndFolloweeTypeAndFolloweeId(Long followerUserId, ActorType followeeType, Long followeeId);

    boolean existsByFollowerUserIdAndFolloweeTypeAndFolloweeIdAndStatus(
            Long followerUserId, ActorType followeeType, Long followeeId, FollowStatus status);

    long countByFolloweeTypeAndFolloweeIdAndStatus(ActorType followeeType, Long followeeId, FollowStatus status);

    long countByFollowerUserIdAndStatus(Long followerUserId, FollowStatus status);

    /** Tat ca chu the (ACTIVE) ma user dang theo doi — dung dung feed. */
    List<Follow> findByFollowerUserIdAndStatus(Long followerUserId, FollowStatus status);

    /** Danh sach follower (ACTIVE) cua 1 chu the. */
    List<Follow> findByFolloweeTypeAndFolloweeIdAndStatus(ActorType followeeType, Long followeeId, FollowStatus status, Pageable pageable);

    /** Yeu cau theo doi dang cho duyet (tai khoan rieng tu). */
    List<Follow> findByFolloweeTypeAndFolloweeIdAndStatusOrderByIdDesc(ActorType followeeType, Long followeeId, FollowStatus status);

    long countByFolloweeTypeAndFolloweeIdAndStatusNot(ActorType followeeType, Long followeeId, FollowStatus status);
}
