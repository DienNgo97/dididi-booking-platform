package com.dididi.booking.social.repository;

import com.dididi.booking.social.domain.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Trang tin moi nhat (id giam dan) — khung chat nap ban dau roi dao nguoc de hien. */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    /** Tin moi hon afterId (id tang dan) — dung cho polling. */
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, long afterId);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    /** Dem tin chua doc (id > lastRead, khong phai do minh gui). */
    long countByConversationIdAndIdGreaterThanAndSenderIdNot(Long conversationId, long afterId, Long senderId);

    /**
     * Như trên nhưng bỏ qua các tin cũ hơn mốc xoá của người xem: sau khi xoá đoạn chat,
     * khung chat chỉ nạp lại từ tin mới về sau.
     */
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdDesc(Long conversationId, long afterId, Pageable pageable);
}
