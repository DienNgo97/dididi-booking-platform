package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndStatusOrderByIdAsc(Long postId, PostStatus status);

    /** Binh luan moi hon afterId (cho polling realtime). */
    List<Comment> findByPostIdAndStatusAndIdGreaterThanOrderByIdAsc(Long postId, PostStatus status, Long id);

    long countByPostIdAndStatus(Long postId, PostStatus status);

    long countByParentIdAndStatus(Long parentId, PostStatus status);

    /** DI-B: đặt likeCount của bình luận bằng 1 câu UPDATE (không đọc-sửa-ghi cả entity). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment c set c.likeCount = :c where c.id = :id")
    int updateLikeCount(@Param("id") Long id, @Param("c") int count);

    // ===== ADMIN kiểm duyệt (native để BỎ QUA @SQLRestriction -> thấy CẢ comment REMOVED) =====

    /** Liệt kê bình luận cho admin: lọc tuỳ chọn theo trạng thái / tác giả / bài. Bao gồm cả REMOVED. */
    @Query(value = "SELECT * FROM social_comments WHERE (:status IS NULL OR status = :status) " +
            "AND (:authorId IS NULL OR author_user_id = :authorId) " +
            "AND (:postId IS NULL OR post_id = :postId) ORDER BY id DESC",
            countQuery = "SELECT COUNT(*) FROM social_comments WHERE (:status IS NULL OR status = :status) " +
            "AND (:authorId IS NULL OR author_user_id = :authorId) " +
            "AND (:postId IS NULL OR post_id = :postId)",
            nativeQuery = true)
    Page<Comment> adminSearch(@Param("status") String status, @Param("authorId") Long authorId,
                              @Param("postId") Long postId, Pageable pageable);

    /** Đếm bình luận theo từng trạng thái (kể cả REMOVED). Trả [status, count]. */
    @Query(value = "SELECT status, COUNT(*) FROM social_comments GROUP BY status", nativeQuery = true)
    List<Object[]> adminCountByStatus();

    /** Khôi phục 1 bình luận đã gỡ: về PUBLISHED + bỏ soft-delete. */
    @Modifying
    @Query(value = "UPDATE social_comments SET status = 'PUBLISHED', deleted_at = NULL WHERE id = :id", nativeQuery = true)
    int adminRestore(@Param("id") Long id);
}
