package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    /** Cac hoi thoai user tham gia (de dung hop thu). */
    List<ConversationParticipant> findByUserId(Long userId);

    /** Thanh vien cua 1 hoi thoai (de tim nguoi con lai trong 1-1). */
    List<ConversationParticipant> findByConversationId(Long conversationId);
}
