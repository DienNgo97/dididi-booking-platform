package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Bookmark;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    List<Bookmark> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<Bookmark> findByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);

    /** DI-B: lưu bài IDEMPOTENT ở tầng DB (unique uk_bookmark_one) — bấm 2 lần không ném lỗi. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT IGNORE INTO social_bookmarks (user_id, post_id, created_at, updated_at) " +
            "VALUES (:userId, :postId, NOW(6), NOW(6))", nativeQuery = true)
    int insertIgnore(@org.springframework.data.repository.query.Param("userId") Long userId,
                     @org.springframework.data.repository.query.Param("postId") Long postId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Bookmark b where b.userId = :userId and b.postId = :postId")
    int deleteBookmark(@org.springframework.data.repository.query.Param("userId") Long userId,
                       @org.springframework.data.repository.query.Param("postId") Long postId);
}
