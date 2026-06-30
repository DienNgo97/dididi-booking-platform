package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostType;
import com.dididi.booking.social.domain.enums.PostVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

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
}
