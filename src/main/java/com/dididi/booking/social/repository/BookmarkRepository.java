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
}
