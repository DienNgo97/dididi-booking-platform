package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipientUserId = :uid and n.read = false")
    int markAllRead(@Param("uid") Long uid);
}
