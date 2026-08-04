package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.SocialProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SocialProfileRepository extends JpaRepository<SocialProfile, Long> {

    Optional<SocialProfile> findByUserId(Long userId);

    Optional<SocialProfile> findByHandle(String handle);

    boolean existsByHandle(String handle);

    List<SocialProfile> findByUserIdIn(Collection<Long> userIds);

    /** Tim ho so theo tu khoa handle/ten (cho mention & goi y). */
    List<SocialProfile> findTop10ByHandleContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String handle, String displayName);

    /** ADMIN: danh sách thành viên có phân trang, tìm theo handle/tên hiển thị. */
    Page<SocialProfile> findByHandleContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String handle, String displayName, Pageable pageable);
}
