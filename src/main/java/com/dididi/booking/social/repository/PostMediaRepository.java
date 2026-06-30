package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPostIdOrderBySortOrderAsc(Long postId);

    List<PostMedia> findByPostIdInOrderByPostIdAscSortOrderAsc(Collection<Long> postIds);

    long countByPostId(Long postId);
}
