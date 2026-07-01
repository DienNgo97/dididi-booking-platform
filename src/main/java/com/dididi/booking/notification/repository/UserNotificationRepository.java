package com.dididi.booking.notification.repository;

import com.dididi.booking.notification.domain.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadFalse(Long recipientUserId);

    @Modifying
    @Query("update UserNotification n set n.read = true where n.recipientUserId = :uid and n.read = false")
    int markAllRead(@Param("uid") Long uid);
}
