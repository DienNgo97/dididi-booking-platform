package com.dididi.booking.support.repository;

import com.dididi.booking.support.domain.SupportMessage;
import com.dididi.booking.support.domain.SupportRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByRole(SupportRole role);

    long countBySource(String source);

    long countByEscalatedTrue();

    @Query("select count(distinct m.conversationId) from SupportMessage m")
    long countConversations();

    @Query("select count(distinct m.conversationId) from SupportMessage m where m.escalated = true")
    long countEscalatedConversations();

    /**
     * Tóm tắt từng hội thoại (mới nhất trước):
     * [0]=conversationId, [1]=count, [2]=min(createdAt), [3]=max(createdAt), [4]=anyEscalated(0/1), [5]=userId.
     */
    @Query("select m.conversationId, count(m), min(m.createdAt), max(m.createdAt), "
            + "max(case when m.escalated = true then 1 else 0 end), max(m.userId) "
            + "from SupportMessage m group by m.conversationId order by max(m.createdAt) desc")
    List<Object[]> conversationSummaries();
}
