package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPairKey(String pairKey);
}
