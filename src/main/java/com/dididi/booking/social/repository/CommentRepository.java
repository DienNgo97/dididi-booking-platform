package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.enums.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndStatusOrderByIdAsc(Long postId, PostStatus status);

    /** Binh luan moi hon afterId (cho polling realtime). */
    List<Comment> findByPostIdAndStatusAndIdGreaterThanOrderByIdAsc(Long postId, PostStatus status, Long id);

    long countByPostIdAndStatus(Long postId, PostStatus status);

    long countByParentIdAndStatus(Long parentId, PostStatus status);
}
