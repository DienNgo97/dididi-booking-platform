package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.api.dto.ActorView;
import com.dididi.booking.social.api.dto.ConversationView;
import com.dididi.booking.social.api.dto.MessageView;
import com.dididi.booking.social.api.dto.PostView;
import com.dididi.booking.social.domain.entity.Conversation;
import com.dididi.booking.social.domain.entity.ConversationParticipant;
import com.dididi.booking.social.domain.entity.Message;
import com.dididi.booking.social.domain.enums.ConversationType;
import com.dididi.booking.social.domain.enums.MessageType;
import com.dididi.booking.social.repository.ConversationParticipantRepository;
import com.dididi.booking.social.repository.ConversationRepository;
import com.dididi.booking.social.repository.MessageRepository;
import com.dididi.booking.social.repository.PostRepository;
import com.dididi.booking.storage.StorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Tin nhan 1-1 (DM): tao hoi thoai, gui (text/anh/chia se bai), poll, dem chua doc, do view. */
@Service
@Transactional
public class MessagingService {

    private static final int THREAD_PAGE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final SocialMediaService mediaService;
    private final UserRepository userRepository;
    private final SocialActorService actorService;
    private final SocialViewService viewService;
    private final PostRepository postRepository;
    private final PostService postService;

    public MessagingService(ConversationRepository conversationRepository,
                            ConversationParticipantRepository participantRepository,
                            MessageRepository messageRepository, SocialMediaService mediaService,
                            UserRepository userRepository, SocialActorService actorService,
                            SocialViewService viewService, PostRepository postRepository,
                            PostService postService) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.mediaService = mediaService;
        this.userRepository = userRepository;
        this.actorService = actorService;
        this.viewService = viewService;
        this.postRepository = postRepository;
        this.postService = postService;
    }

    public Conversation getOrCreateDirect(Long meId, Long otherId) {
        if (meId.equals(otherId)) {
            throw new BusinessException("DM_SELF", "Không thể tự nhắn cho chính mình", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.findById(otherId).isEmpty()) {
            throw new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND);
        }
        String pk = pairKey(meId, otherId);
        Conversation existing = conversationRepository.findByPairKey(pk).orElse(null);
        if (existing != null) {
            return existing;
        }
        Conversation c = new Conversation();
        c.setType(ConversationType.DIRECT);
        c.setPairKey(pk);
        c.setCreatedBy(meId);
        c = conversationRepository.save(c);
        addParticipant(c.getId(), meId);
        addParticipant(c.getId(), otherId);
        return c;
    }

    public Message sendText(Long meId, Long convId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("EMPTY_MESSAGE", "Tin nhắn trống", HttpStatus.BAD_REQUEST);
        }
        return createMessage(meId, convId, MessageType.TEXT, content.trim(), null, null, null);
    }

    public Message sendImage(Long meId, Long convId, MultipartFile file) {
        String key = mediaService.uploadMessageImage(file);
        return createMessage(meId, convId, MessageType.IMAGE, null, key, file.getContentType(), null);
    }

    public Message sharePost(Long meId, Long convId, Long postId, String text) {
        // Chi cho chia se bai ma nguoi gui duoc phep xem (chong ro ri bai rieng tu qua DM).
        postService.getForView(meId, postId);
        return createMessage(meId, convId, MessageType.POST_SHARE,
                text != null && !text.isBlank() ? text.trim() : null, null, null, postId);
    }

    private Message createMessage(Long meId, Long convId, MessageType type, String content,
                                  String attachmentKey, String contentType, Long sharedPostId) {
        Conversation c = requireConversation(convId, meId);
        Message m = new Message();
        m.setConversationId(convId);
        m.setSenderId(meId);
        m.setType(type);
        m.setContent(content);
        m.setAttachmentKey(attachmentKey);
        m.setContentType(contentType);
        m.setSharedPostId(sharedPostId);
        m = messageRepository.save(m);
        c.setLastMessageAt(Instant.now());
        c.setLastMessagePreview(previewFor(type, content));
        conversationRepository.save(c);
        // nguoi gui da doc toi tin cua minh
        Message saved = m;
        participantRepository.findByConversationIdAndUserId(convId, meId).ifPresent(p -> {
            p.setLastReadMessageId(saved.getId());
            participantRepository.save(p);
        });
        return m;
    }

    @Transactional(readOnly = true)
    public Conversation requireConversation(Long convId, Long meId) {
        Conversation c = conversationRepository.findById(convId)
                .orElseThrow(() -> new BusinessException("CONV_NOT_FOUND", "Không tìm thấy hội thoại", HttpStatus.NOT_FOUND));
        if (!participantRepository.existsByConversationIdAndUserId(convId, meId)) {
            throw new BusinessException("FORBIDDEN", "Bạn không thuộc hội thoại này", HttpStatus.FORBIDDEN);
        }
        return c;
    }

    /** Trang tin (cu -> moi) + danh dau da doc. */
    public List<Message> messages(Long convId, Long meId) {
        requireConversation(convId, meId);
        List<Message> latest = new ArrayList<>(
                messageRepository.findByConversationIdOrderByIdDesc(convId, PageRequest.of(0, THREAD_PAGE)));
        Collections.reverse(latest);
        markReadInternal(convId, meId);
        return latest;
    }

    /** Tin moi hon afterId (polling) + danh dau da doc. */
    public List<Message> newMessages(Long convId, Long meId, long afterId) {
        requireConversation(convId, meId);
        List<Message> list = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(convId, afterId);
        markReadInternal(convId, meId);
        return list;
    }

    public void markRead(Long convId, Long meId) {
        requireConversation(convId, meId);
        markReadInternal(convId, meId);
    }

    private void markReadInternal(Long convId, Long meId) {
        messageRepository.findFirstByConversationIdOrderByIdDesc(convId).ifPresent(last ->
                participantRepository.findByConversationIdAndUserId(convId, meId).ifPresent(p -> {
                    if (p.getLastReadMessageId() < last.getId()) {
                        p.setLastReadMessageId(last.getId());
                        participantRepository.save(p);
                    }
                }));
    }

    @Transactional(readOnly = true)
    public StorageService.StoredObject loadImage(Long messageId, Long meId) {
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy ảnh", HttpStatus.NOT_FOUND));
        if (!participantRepository.existsByConversationIdAndUserId(m.getConversationId(), meId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xem ảnh này", HttpStatus.FORBIDDEN);
        }
        return mediaService.loadByKey(m.getAttachmentKey());
    }

    // ---------- view ----------

    @Transactional(readOnly = true)
    public List<ConversationView> inbox(Long meId) {
        List<ConversationView> out = new ArrayList<>();
        for (ConversationParticipant p : participantRepository.findByUserId(meId)) {
            Conversation c = conversationRepository.findById(p.getConversationId()).orElse(null);
            if (c == null) {
                continue;
            }
            Long otherId = otherUserId(c.getId(), meId);
            ActorView other = otherId != null ? actorService.userActor(otherId)
                    : new ActorView("USER", 0L, "Người dùng", null, null, "#");
            int unread = (int) messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(
                    c.getId(), p.getLastReadMessageId(), meId);
            long ts = c.getLastMessageAt() != null ? c.getLastMessageAt().toEpochMilli()
                    : (c.getCreatedAt() != null ? c.getCreatedAt().toEpochMilli() : 0L);
            out.add(new ConversationView(c.getId(), other, c.getLastMessagePreview(), ts, unread,
                    "/community/messages/" + c.getId()));
        }
        out.sort((a, b) -> Long.compare(b.getLastMessageAtMs(), a.getLastMessageAtMs()));
        return out;
    }

    @Transactional(readOnly = true)
    public int dmUnreadTotal(Long meId) {
        int total = 0;
        for (ConversationParticipant p : participantRepository.findByUserId(meId)) {
            total += (int) messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(
                    p.getConversationId(), p.getLastReadMessageId(), meId);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public Long otherUserId(Long convId, Long meId) {
        return participantRepository.findByConversationId(convId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(meId))
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public ActorView otherActor(Long convId, Long meId) {
        Long otherId = otherUserId(convId, meId);
        return otherId != null ? actorService.userActor(otherId)
                : new ActorView("USER", 0L, "Người dùng", null, null, "#");
    }

    public List<MessageView> toMessageViews(List<Message> messages, Long meId) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Set<Long> senderIds = messages.stream().map(Message::getSenderId).collect(Collectors.toSet());
        Map<Long, ActorView> senders = actorService.batchUserActors(senderIds);

        Set<Long> sharedIds = messages.stream().map(Message::getSharedPostId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PostView> sharedViews = new HashMap<>();
        if (!sharedIds.isEmpty()) {
            for (PostView pv : viewService.toPostViews(postRepository.findAllById(sharedIds), meId, false)) {
                sharedViews.put(pv.getId(), pv);
            }
        }

        List<MessageView> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            ActorView s = senders.getOrDefault(m.getSenderId(), actorService.userActor(m.getSenderId()));
            boolean mine = m.getSenderId().equals(meId);
            String mediaUrl = m.getType() == MessageType.IMAGE ? "/community/messages/media/" + m.getId() : null;
            PostView shared = m.getSharedPostId() != null ? sharedViews.get(m.getSharedPostId()) : null;
            out.add(new MessageView(m.getId(), mine, s, m.getType().name(), m.getContent(), mediaUrl, shared,
                    m.getCreatedAt() != null ? m.getCreatedAt().toEpochMilli() : 0L));
        }
        return out;
    }

    // ---------- helpers ----------

    private void addParticipant(Long convId, Long userId) {
        ConversationParticipant p = new ConversationParticipant();
        p.setConversationId(convId);
        p.setUserId(userId);
        p.setLastReadMessageId(0);
        participantRepository.save(p);
    }

    private static String pairKey(Long a, Long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return lo + ":" + hi;
    }

    private static String previewFor(MessageType type, String content) {
        String s = switch (type) {
            case IMAGE -> "[Hình ảnh]";
            case POST_SHARE -> "[Đã chia sẻ một bài viết]";
            case TEXT -> content != null ? content : "";
        };
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
