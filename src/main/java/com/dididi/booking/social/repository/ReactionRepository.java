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

    /**
     * DI-B — THẢ TIM IDEMPOTENT Ở TẦNG DB: INSERT IGNORE dựa trên unique uk_reaction_one.
     * Bấm nhanh 2 lần / 2 tab -> bản ghi thứ 2 bị BỎ QUA (trả 0 dòng), KHÔNG ném lỗi.
     *
     * Vì sao không bắt DataIntegrityViolationException? Khi ràng buộc bị vi phạm, Hibernate đánh dấu
     * transaction rollback-only và persistence context hỏng -> mọi thao tác sau đó ném AssertionFailure
     * (đã thực nghiệm: 5/6 request song song trả 500). Chặn từ tầng SQL là cách duy nhất sạch.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT IGNORE INTO social_reactions (user_id, target_type, target_id, type, created_at, updated_at) " +
            "VALUES (:userId, :targetType, :targetId, 'LIKE', NOW(6), NOW(6))", nativeQuery = true)
    int insertIgnoreLike(@org.springframework.data.repository.query.Param("userId") Long userId,
                         @org.springframework.data.repository.query.Param("targetType") String targetType,
                         @org.springframework.data.repository.query.Param("targetId") Long targetId);

    /** Xoá tim theo khoá nghiệp vụ (1 câu DELETE, không cần load entity). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "delete from Reaction r where r.userId = :userId and r.targetType = :targetType and r.targetId = :targetId")
    int deleteLike(@org.springframework.data.repository.query.Param("userId") Long userId,
                   @org.springframework.data.repository.query.Param("targetType") ReactionTarget targetType,
                   @org.springframework.data.repository.query.Param("targetId") Long targetId);

    /** Cac doi tuong (trong danh sach) ma user da like — de to dam trang thai tim tren feed. */
    List<Reaction> findByUserIdAndTargetTypeAndTargetIdIn(Long userId, ReactionTarget targetType, Collection<Long> targetIds);
}
