package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    List<PostHashtag> findByPostId(Long postId);

    boolean existsByPostIdAndHashtagId(Long postId, Long hashtagId);
}
