package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.PostMention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostMentionRepository extends JpaRepository<PostMention, Long> {

    List<PostMention> findByPostId(Long postId);
}
