package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    Optional<Hashtag> findByTag(String tag);

    /** Hashtag thinh hanh: nhieu bai nhat. */
    List<Hashtag> findTop10ByPostCountGreaterThanOrderByPostCountDesc(int min);

    /** Tim hashtag theo tu khoa (tim kiem toan cuc). */
    List<Hashtag> findTop10ByTagContainingIgnoreCaseOrderByPostCountDesc(String tag);
}
