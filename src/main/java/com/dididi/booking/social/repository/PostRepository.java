package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ===== DI-B: cập nhật ĐẾM NGUYÊN TỬ (1 câu UPDATE) thay vì đọc entity -> set -> save.
    // Đọc-sửa-ghi bị lost update khi 2 request song song, và save() còn ghi đè MỌI cột khác
    // của bài viết bằng bản chụp cũ trong bộ nhớ. Các câu dưới chỉ đụng đúng 1 cột đếm.

    /** Đặt likeCount = giá trị COUNT(*) vừa đo (idempotent, không phụ thuộc giá trị cũ). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = :c where p.id = :id")
    int updateLikeCount(@Param("id") Long id, @Param("c") int count);

    /** Đặt commentCount = giá trị COUNT(*) vừa đo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.commentCount = :c where p.id = :id")
    int updateCommentCount(@Param("id") Long id, @Param("c") int count);

    /**
     * Khoá bi quan dòng bài viết (SELECT ... FOR UPDATE) — dùng cho TOGGLE REPOST:
     * "kiểm tra đã repost chưa -> tạo/gỡ" phải nguyên tử, nếu không nhiều request song song
     * đều thấy "chưa repost" và cùng tạo (đã thực nghiệm: 6 request -> 6 bài repost trùng).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    /** Cộng/trừ repostCount ngay trong DB, kẹp không âm (delta = +1 / -1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.repostCount = case when p.repostCount + :delta < 0 then 0 else p.repostCount + :delta end where p.id = :id")
    int bumpRepostCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * Feed cá nhân hoá: bài (PUBLISHED) của các chủ thể user/hotel mà mình theo dõi + của mình.
     * Keyset theo id giảm dần (cursor = Long.MAX_VALUE cho trang đầu).
     */
    @Query("select p from Post p where p.status = :pub and p.id < :cursor " +
            "and (p.visibility <> :priv or p.authorUserId = :viewer) and (" +
            "(p.actorType = :userType and p.actorId in :userIds) or " +
            "(p.actorType = :hotelType and p.actorId in :hotelIds)) " +
            "order by p.id desc")
    List<Post> feed(@Param("pub") PostStatus pub,
                    @Param("priv") PostVisibility priv,
                    @Param("viewer") Long viewer,
                    @Param("userType") ActorType userType,
                    @Param("hotelType") ActorType hotelType,
                    @Param("userIds") Collection<Long> userIds,
                    @Param("hotelIds") Collection<Long> hotelIds,
                    @Param("cursor") long cursor,
                    Pageable pageable);

    /** Bài của 1 chủ thể (trang cá nhân / trang khách sạn), keyset id giảm dần. */
    List<Post> findByActorTypeAndActorIdAndStatusAndIdLessThanOrderByIdDesc(
            ActorType actorType, Long actorId, PostStatus status, long cursor, Pageable pageable);

    long countByActorTypeAndActorIdAndStatus(ActorType actorType, Long actorId, PostStatus status);

    /** Khám phá tạm thời (P1): bài công khai mới nhất của mọi người (P2 sẽ thay bằng trending). */
    @Query("select p from Post p where p.status = :pub and p.visibility = com.dididi.booking.social.domain.enums.PostVisibility.PUBLIC " +
            "and p.id < :cursor order by p.id desc")
    List<Post> explore(@Param("pub") PostStatus pub, @Param("cursor") long cursor, Pageable pageable);

    /** Bài công khai mang 1 hashtag, keyset id giảm dần. */
    @Query("select p from Post p, PostHashtag ph where ph.hashtagId = :hid and ph.postId = p.id " +
            "and p.status = :pub and p.visibility = com.dididi.booking.social.domain.enums.PostVisibility.PUBLIC " +
            "and p.id < :cursor order by p.id desc")
    List<Post> postsByHashtag(@Param("hid") Long hashtagId, @Param("pub") PostStatus pub,
                              @Param("cursor") long cursor, Pageable pageable);

    /** Tim kiem bai cong khai theo tu khoa trong caption (tim kiem toan cuc). */
    @Query("select p from Post p where p.status = :pub " +
            "and p.visibility = com.dididi.booking.social.domain.enums.PostVisibility.PUBLIC " +
            "and p.caption is not null and lower(p.caption) like lower(concat('%', :q, '%')) " +
            "order by p.id desc")
    List<Post> searchPublic(@Param("pub") PostStatus pub, @Param("q") String q, Pageable pageable);

    /** Bài repost của user cho 1 bài gốc (để biết đã repost chưa / để gỡ repost). */
    Optional<Post> findFirstByAuthorUserIdAndTypeAndOriginPostIdAndStatus(
            Long authorUserId, PostType type, Long originPostId, PostStatus status);

    /** Trạng thái "đã repost" cho 1 lô bài gốc. */
    List<Post> findByAuthorUserIdAndTypeAndStatusAndOriginPostIdIn(
            Long authorUserId, PostType type, PostStatus status, Collection<Long> originPostIds);

    // ===== ADMIN kiểm duyệt (native để BỎ QUA @SQLRestriction -> thấy CẢ bài REMOVED) =====

    /** Liệt kê bài cho admin: lọc tuỳ chọn theo trạng thái / tác giả / từ khoá caption. Bao gồm cả REMOVED. */
    @Query(value = "SELECT * FROM social_posts WHERE (:status IS NULL OR status = :status) " +
            "AND (:authorId IS NULL OR author_user_id = :authorId) " +
            "AND (:q IS NULL OR LOWER(caption) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY id DESC",
            countQuery = "SELECT COUNT(*) FROM social_posts WHERE (:status IS NULL OR status = :status) " +
            "AND (:authorId IS NULL OR author_user_id = :authorId) " +
            "AND (:q IS NULL OR LOWER(caption) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Post> adminSearch(@Param("status") String status, @Param("authorId") Long authorId,
                           @Param("q") String q, Pageable pageable);

    /** Đếm bài theo từng trạng thái (kể cả REMOVED) cho dashboard. Trả [status, count]. */
    @Query(value = "SELECT status, COUNT(*) FROM social_posts GROUP BY status", nativeQuery = true)
    List<Object[]> adminCountByStatus();

    /** Khôi phục 1 bài đã gỡ: về PUBLISHED + bỏ soft-delete. */
    @Modifying
    @Query(value = "UPDATE social_posts SET status = 'PUBLISHED', deleted_at = NULL WHERE id = :id", nativeQuery = true)
    int adminRestore(@Param("id") Long id);
}
