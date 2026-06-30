package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Reaction;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByUserIdAndTargetTypeAndTargetId(Long userId, ReactionTarget targetType, Long targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, ReactionTarget targetType, Long targetId);

    long countByTargetTypeAndTargetId(ReactionTarget targetType, Long targetId);

    /** Cac doi tuong (trong danh sach) ma user da like — de to dam trang thai tim tren feed. */
    List<Reaction> findByUserIdAndTargetTypeAndTargetIdIn(Long userId, ReactionTarget targetType, Collection<Long> targetIds);
}
