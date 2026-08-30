package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.i18n.I18nSupport;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tin nhan: hoi thoai 1-1 va NHOM, gui (text/anh/chia se bai), poll, dem chua doc, do view,
 * xoa/luu tru doan chat (chi anh huong phia nguoi bam), quan ly thanh vien nhom.
 */
@Service
@Transactional
public class MessagingService {

    private static final int THREAD_PAGE = 50;
    /** Chặn nhóm phình vô hạn: mỗi tin nhắn phải quét toàn bộ thành viên để bỏ ẩn/lưu trữ. */
    private static final int MAX_GROUP_MEMBERS = 50;

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
            // Đã từng xoá/lưu trữ đoạn chat này rồi giờ nhắn lại: đưa nó về hộp thư chính.
            // Vẫn giữ mốc xoá nên lịch sử cũ không bị lôi lại.
            participantRepository.findByConversationIdAndUserId(existing.getId(), meId).ifPresent(p -> {
                if (p.getHiddenAt() != null || p.getArchivedAt() != null) {
                    p.setHiddenAt(null);
                    p.setArchivedAt(null);
                    participantRepository.save(p);
                }
            });
            return existing;
        }
        Conversation c = new Conversation();
        c.setType(ConversationType.DIRECT);
        c.setPairKey(pk);
        c.setCreatedBy(meId);
        c = conversationRepository.save(c);
        addParticipant(c.getId(), meId, false);
        addParticipant(c.getId(), otherId, false);
        return c;
    }

    // ---------- nhóm chat ----------

    /** Tạo nhóm: người tạo là chủ nhóm; thành viên nào cũng mời thêm được, chỉ chủ nhóm xoá/đổi tên. */
    public Conversation createGroup(Long meId, String title, List<Long> memberIds) {
        String name = title != null ? title.trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException("GROUP_NO_TITLE", "Vui lòng đặt tên nhóm", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (id != null && !id.equals(meId)) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("GROUP_NO_MEMBER", "Chọn ít nhất một người để tạo nhóm", HttpStatus.BAD_REQUEST);
        }
        if (ids.size() + 1 > MAX_GROUP_MEMBERS) {
            throw new BusinessException("GROUP_TOO_BIG",
                    "Nhóm tối đa " + MAX_GROUP_MEMBERS + " thành viên", HttpStatus.BAD_REQUEST);
        }
        for (Long id : ids) {
            if (userRepository.findById(id).isEmpty()) {
                throw new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND);
            }
        }
        Conversation c = new Conversation();
        c.setType(ConversationType.GROUP);
        c.setPairKey(null);
        c.setTitle(name);
        c.setCreatedBy(meId);
        c = conversationRepository.save(c);
        addParticipant(c.getId(), meId, true);
        for (Long id : ids) {
            addParticipant(c.getId(), id, false);
        }
        systemMessage(c, meId, I18nSupport.msg("community.dm.sys.created", "{0} đã tạo nhóm “{1}”",
                displayName(meId), name));
        return c;
    }

    /** Thêm thành viên — theo yêu cầu, MỌI thành viên trong nhóm đều thêm được. */
    public void addMembers(Long meId, Long convId, List<Long> userIds) {
        Conversation c = requireGroup(convId, meId);
        List<ConversationParticipant> current = participantRepository.findByConversationIdAndLeftAtIsNull(convId);
        Set<Long> already = current.stream().map(ConversationParticipant::getUserId).collect(Collectors.toSet());
        List<String> added = new ArrayList<>();
        int count = current.size();
        // Người mới vào chỉ đọc được từ đây trở đi — không moi lại đoạn hội thoại trước khi họ vào.
        long floor = messageRepository.findFirstByConversationIdOrderByIdDesc(convId)
                .map(Message::getId).orElse(0L);
        for (Long id : userIds == null ? List.<Long>of() : userIds) {
            if (id == null || already.contains(id)) {
                continue;
            }
            if (userRepository.findById(id).isEmpty()) {
                throw new BusinessException("NO_USER", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND);
            }
            if (++count > MAX_GROUP_MEMBERS) {
                throw new BusinessException("GROUP_TOO_BIG",
                        "Nhóm tối đa " + MAX_GROUP_MEMBERS + " thành viên", HttpStatus.BAD_REQUEST);
            }
            // Người từng rời nhóm: dùng lại bản ghi cũ để không vi phạm unique (conversation_id, user_id).
            ConversationParticipant old = participantRepository.findByConversationIdAndUserId(convId, id).orElse(null);
            if (old != null) {
                old.setLeftAt(null);
                old.setHiddenAt(null);
                old.setArchivedAt(null);
                old.setClearedBeforeMessageId(floor);
                old.setLastReadMessageId(floor);
                participantRepository.save(old);
            } else {
                addParticipant(convId, id, false, floor);
            }
            already.add(id);
            added.add(displayName(id));
        }
        if (!added.isEmpty()) {
            systemMessage(c, meId, I18nSupport.msg("community.dm.sys.added", "{0} đã thêm {1} vào nhóm",
                    displayName(meId), String.join(", ", added)));
        }
    }

    /** Xoá thành viên — chỉ chủ nhóm. */
    public void removeMember(Long meId, Long convId, Long userId) {
        Conversation c = requireGroup(convId, meId);
        requireOwner(convId, meId);
        if (meId.equals(userId)) {
            throw new BusinessException("OWNER_SELF_REMOVE",
                    "Chủ nhóm hãy dùng chức năng Rời nhóm", HttpStatus.BAD_REQUEST);
        }
        ConversationParticipant p = participantRepository.findByConversationIdAndUserId(convId, userId)
                .filter(x -> x.getLeftAt() == null)
                .orElseThrow(() -> new BusinessException("NOT_MEMBER", "Người này không ở trong nhóm", HttpStatus.NOT_FOUND));
        p.setLeftAt(Instant.now());
        participantRepository.save(p);
        systemMessage(c, meId, I18nSupport.msg("community.dm.sys.removed", "{0} đã xoá {1} khỏi nhóm",
                displayName(meId), displayName(userId)));
    }

    /** Đổi tên nhóm — chỉ chủ nhóm. */
    public void renameGroup(Long meId, Long convId, String title) {
        Conversation c = requireGroup(convId, meId);
        requireOwner(convId, meId);
        String name = title != null ? title.trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException("GROUP_NO_TITLE", "Tên nhóm không được để trống", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        c.setTitle(name);
        conversationRepository.save(c);
        systemMessage(c, meId, I18nSupport.msg("community.dm.sys.renamed", "{0} đã đổi tên nhóm thành “{1}”",
                displayName(meId), name));
    }

    /**
     * Rời nhóm. Chủ nhóm rời thì quyền chủ chuyển cho thành viên vào sớm nhất — nếu không, nhóm sẽ
     * kẹt vĩnh viễn: không ai đổi được tên hay xoá được thành viên.
     */
    public void leaveGroup(Long meId, Long convId) {
        Conversation c = requireGroup(convId, meId);
        ConversationParticipant me = requireActiveParticipant(convId, meId);
        me.setLeftAt(Instant.now());
        me.setOwner(false);
        participantRepository.save(me);
        systemMessage(c, meId, I18nSupport.msg("community.dm.sys.left", "{0} đã rời nhóm", displayName(meId)));

        List<ConversationParticipant> rest = participantRepository.findByConversationIdAndLeftAtIsNull(convId);
        if (!rest.isEmpty() && rest.stream().noneMatch(ConversationParticipant::isOwner)) {
            ConversationParticipant next = rest.stream()
                    .min(java.util.Comparator.comparing(ConversationParticipant::getId)).orElse(rest.get(0));
            next.setOwner(true);
            participantRepository.save(next);
            systemMessage(c, meId, I18nSupport.msg("community.dm.sys.newOwner", "{0} trở thành chủ nhóm",
                    displayName(next.getUserId())));
        }
    }

    // ---------- xoá / lưu trữ đoạn chat ----------

    /**
     * XOÁ ĐOẠN CHAT ở phía người bấm: ẩn khỏi hộp thư + đặt mốc xoá. Không đụng vào tin nhắn thật vì
     * bản sao lịch sử bên kia vẫn là của họ. Người kia nhắn tiếp thì hội thoại quay lại từ tin mới.
     */
    public void deleteConversation(Long meId, Long convId) {
        requireConversation(convId, meId);
        ConversationParticipant p = requireActiveParticipant(convId, meId);
        long lastId = messageRepository.findFirstByConversationIdOrderByIdDesc(convId)
                .map(Message::getId).orElse(0L);
        p.setHiddenAt(Instant.now());
        p.setArchivedAt(null);
        p.setClearedBeforeMessageId(lastId);
        p.setLastReadMessageId(Math.max(p.getLastReadMessageId(), lastId));
        participantRepository.save(p);
    }

    /** LƯU TRỮ: chuyển sang mục riêng, giữ nguyên lịch sử; có tin mới thì tự quay về hộp thư chính. */
    public void archiveConversation(Long meId, Long convId, boolean archived) {
        requireConversation(convId, meId);
        ConversationParticipant p = requireActiveParticipant(convId, meId);
        p.setArchivedAt(archived ? Instant.now() : null);
        p.setHiddenAt(null);
        participantRepository.save(p);
    }

    public Message sendText(Long meId, Long convId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("EMPTY_MESSAGE", "Tin nhắn trống", HttpStatus.BAD_REQUEST);
        }
        return createMessage(meId, convId, MessageType.TEXT, content.trim(), null, null, null);
    }

    public Message sendImage(Long meId, Long convId, MultipartFile file) {
        // Kiểm tra quyền TRƯỚC khi upload: rollback giao dịch không xoá được file đã nằm trong MinIO,
        // nên người lạ bắn ảnh vào hội thoại không thuộc về mình sẽ để lại rác vĩnh viễn.
        requireConversation(convId, meId);
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
        touchParticipants(convId, meId, m.getId());
        return m;
    }

    @Transactional(readOnly = true)
    public Conversation requireConversation(Long convId, Long meId) {
        Conversation c = conversationRepository.findById(convId)
                .orElseThrow(() -> new BusinessException("CONV_NOT_FOUND", "Không tìm thấy hội thoại", HttpStatus.NOT_FOUND));
        if (!participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(convId, meId)) {
            throw new BusinessException("FORBIDDEN", "Bạn không thuộc hội thoại này", HttpStatus.FORBIDDEN);
        }
        return c;
    }

    /** Trang tin (cu -> moi) + danh dau da doc. Bo qua doan da xoa (id <= mocXoa). */
    public List<Message> messages(Long convId, Long meId) {
        requireConversation(convId, meId);
        long floor = clearedFloor(convId, meId);
        List<Message> latest = new ArrayList<>(messageRepository
                .findByConversationIdAndIdGreaterThanOrderByIdDesc(convId, floor, PageRequest.of(0, THREAD_PAGE)));
        Collections.reverse(latest);
        markReadInternal(convId, meId);
        return latest;
    }

    /** Tin moi hon afterId (polling) + danh dau da doc. */
    public List<Message> newMessages(Long convId, Long meId, long afterId) {
        requireConversation(convId, meId);
        long from = Math.max(afterId, clearedFloor(convId, meId));
        List<Message> list = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(convId, from);
        markReadInternal(convId, meId);
        return list;
    }

    private long clearedFloor(Long convId, Long meId) {
        return participantRepository.findByConversationIdAndUserId(convId, meId)
                .map(ConversationParticipant::getClearedBeforeMessageId).orElse(0L);
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
        ConversationParticipant p = participantRepository
                .findByConversationIdAndUserId(m.getConversationId(), meId)
                .filter(x -> x.getLeftAt() == null)
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "Không có quyền xem ảnh này", HttpStatus.FORBIDDEN));
        // Ảnh thuộc đoạn đã xoá thì cũng không mở được nữa (link cũ dán lại cũng vô hiệu).
        if (m.getId() <= p.getClearedBeforeMessageId()) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xem ảnh này", HttpStatus.FORBIDDEN);
        }
        return mediaService.loadByKey(m.getAttachmentKey());
    }

    // ---------- view ----------

    /** Hộp thư chính: bỏ hội thoại đã xoá (ẩn) và đã lưu trữ. */
    @Transactional(readOnly = true)
    public List<ConversationView> inbox(Long meId) {
        return inboxInternal(meId, false);
    }

    /** Mục "Lưu trữ": chỉ những hội thoại người dùng chủ động cất đi. */
    @Transactional(readOnly = true)
    public List<ConversationView> archivedInbox(Long meId) {
        return inboxInternal(meId, true);
    }

    private List<ConversationView> inboxInternal(Long meId, boolean archived) {
        List<ConversationView> out = new ArrayList<>();
        for (ConversationParticipant p : participantRepository.findByUserIdAndLeftAtIsNull(meId)) {
            if (p.getHiddenAt() != null) {
                continue;                                   // đã xoá đoạn chat -> không hiện ở đâu cả
            }
            if (archived != (p.getArchivedAt() != null)) {
                continue;
            }
            Conversation c = conversationRepository.findById(p.getConversationId()).orElse(null);
            if (c == null) {
                continue;
            }
            long ts = c.getLastMessageAt() != null ? c.getLastMessageAt().toEpochMilli()
                    : (c.getCreatedAt() != null ? c.getCreatedAt().toEpochMilli() : 0L);
            boolean group = c.getType() == ConversationType.GROUP;
            out.add(new ConversationView(c.getId(), conversationActor(c, meId), c.getLastMessagePreview(), ts,
                    unreadOf(p, meId), "/community/messages/" + c.getId(), group, p.getArchivedAt() != null,
                    group ? participantRepository.findByConversationIdAndLeftAtIsNull(c.getId()).size() : 2));
        }
        out.sort((a, b) -> Long.compare(b.getLastMessageAtMs(), a.getLastMessageAtMs()));
        return out;
    }

    /** Chưa đọc tính từ mốc lớn hơn giữa "đã đọc" và "mốc xoá" — đoạn đã xoá không được đếm lại. */
    private int unreadOf(ConversationParticipant p, Long meId) {
        long from = Math.max(p.getLastReadMessageId(), p.getClearedBeforeMessageId());
        return (int) messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(
                p.getConversationId(), from, meId);
    }

    @Transactional(readOnly = true)
    public int dmUnreadTotal(Long meId) {
        int total = 0;
        for (ConversationParticipant p : participantRepository.findByUserIdAndLeftAtIsNull(meId)) {
            // Chỉ đếm những gì ĐANG ở hộp thư chính. Đếm cả mục đã xoá/lưu trữ thì chuông báo số
            // mà người dùng không có cách nào bấm vào để xoá số đó đi.
            if (p.getHiddenAt() != null || p.getArchivedAt() != null) {
                continue;
            }
            total += unreadOf(p, meId);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public Long otherUserId(Long convId, Long meId) {
        return participantRepository.findByConversationIdAndLeftAtIsNull(convId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(meId))
                .findFirst().orElse(null);
    }

    /** Tiêu đề khung chat: 1-1 lấy người kia, nhóm lấy tên nhóm (không avatar, không link hồ sơ). */
    @Transactional(readOnly = true)
    public ActorView otherActor(Long convId, Long meId) {
        Conversation c = conversationRepository.findById(convId).orElse(null);
        if (c == null) {
            return new ActorView("USER", 0L, "Người dùng", null, null, "#");
        }
        return conversationActor(c, meId);
    }

    private ActorView conversationActor(Conversation c, Long meId) {
        if (c.getType() == ConversationType.GROUP) {
            String name = c.getTitle() != null && !c.getTitle().isBlank() ? c.getTitle()
                    : I18nSupport.msg("community.dm.groupDefault", "Nhóm chat");
            return new ActorView("GROUP", c.getId(), name, null, null, "/community/messages/" + c.getId());
        }
        Long otherId = otherUserId(c.getId(), meId);
        return otherId != null ? actorService.userActor(otherId)
                : new ActorView("USER", 0L, "Người dùng", null, null, "#");
    }

    /** Danh sách thành viên nhóm (để hiện bảng quản lý); phần tử đầu là chủ nhóm. */
    @Transactional(readOnly = true)
    public List<ActorView> members(Long convId, Long meId) {
        requireConversation(convId, meId);
        List<ConversationParticipant> ps = new ArrayList<>(
                participantRepository.findByConversationIdAndLeftAtIsNull(convId));
        ps.sort((a, b) -> a.isOwner() == b.isOwner() ? Long.compare(a.getId(), b.getId()) : (a.isOwner() ? -1 : 1));
        List<ActorView> out = new ArrayList<>(ps.size());
        for (ConversationParticipant p : ps) {
            out.add(actorService.userActor(p.getUserId()));
        }
        return out;
    }

    /** Người xem có phải chủ nhóm không (UI ẩn/hiện nút đổi tên, xoá thành viên). */
    @Transactional(readOnly = true)
    public boolean isOwner(Long convId, Long meId) {
        return participantRepository.findByConversationIdAndUserId(convId, meId)
                .filter(p -> p.getLeftAt() == null).map(ConversationParticipant::isOwner).orElse(false);
    }

    /** Hội thoại này đang nằm trong mục Lưu trữ của người xem — để nút trong khung chat đổi chiều. */
    @Transactional(readOnly = true)
    public boolean isArchived(Long convId, Long meId) {
        return participantRepository.findByConversationIdAndUserId(convId, meId)
                .map(p -> p.getArchivedAt() != null).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isGroup(Long convId) {
        return conversationRepository.findById(convId)
                .map(c -> c.getType() == ConversationType.GROUP).orElse(false);
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

    private void addParticipant(Long convId, Long userId, boolean owner) {
        addParticipant(convId, userId, owner, 0L);
    }

    /** floor = mốc bắt đầu nhìn thấy: 0 khi hội thoại vừa tạo, id tin cuối khi thêm vào nhóm đang chạy. */
    private void addParticipant(Long convId, Long userId, boolean owner, long floor) {
        ConversationParticipant p = new ConversationParticipant();
        p.setConversationId(convId);
        p.setUserId(userId);
        p.setLastReadMessageId(floor);
        p.setClearedBeforeMessageId(floor);
        p.setOwner(owner);
        participantRepository.save(p);
    }

    private Conversation requireGroup(Long convId, Long meId) {
        Conversation c = requireConversation(convId, meId);
        if (c.getType() != ConversationType.GROUP) {
            throw new BusinessException("NOT_GROUP", "Hội thoại này không phải nhóm", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    private ConversationParticipant requireActiveParticipant(Long convId, Long meId) {
        return participantRepository.findByConversationIdAndUserId(convId, meId)
                .filter(p -> p.getLeftAt() == null)
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "Bạn không thuộc hội thoại này", HttpStatus.FORBIDDEN));
    }

    private void requireOwner(Long convId, Long meId) {
        if (!requireActiveParticipant(convId, meId).isOwner()) {
            throw new BusinessException("NOT_GROUP_OWNER", "Chỉ chủ nhóm làm được việc này", HttpStatus.FORBIDDEN);
        }
    }

    /** Dòng thông báo hệ thống — cũng cập nhật xem-trước để hộp thư phản ánh đúng việc vừa xảy ra. */
    private void systemMessage(Conversation c, Long actorId, String text) {
        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSenderId(actorId);
        m.setType(MessageType.SYSTEM);
        m.setContent(text);
        m = messageRepository.save(m);
        c.setLastMessageAt(Instant.now());
        c.setLastMessagePreview(previewFor(MessageType.SYSTEM, text));
        conversationRepository.save(c);
        // Dùng chung với tin thường: nếu không, người đã lưu trữ/xoá sẽ bị +1 chưa đọc mà hội thoại
        // vẫn nằm ngoài hộp thư — số badge không bao giờ về 0 được.
        touchParticipants(c.getId(), actorId, m.getId());
    }

    /**
     * Sau mỗi tin: người gửi coi như đã đọc; mọi người còn lại nếu đang ẩn/lưu trữ thì đưa hội thoại
     * quay về hộp thư chính (đúng yêu cầu "có tin mới thì tự quay về").
     */
    private void touchParticipants(Long convId, Long actorId, long messageId) {
        for (ConversationParticipant p : participantRepository.findByConversationIdAndLeftAtIsNull(convId)) {
            boolean mine = p.getUserId().equals(actorId);
            if (mine) {
                p.setLastReadMessageId(messageId);
            } else if (p.getHiddenAt() == null && p.getArchivedAt() == null) {
                continue;
            }
            p.setHiddenAt(null);
            p.setArchivedAt(null);
            participantRepository.save(p);
        }
    }

    private String displayName(Long userId) {
        ActorView a = actorService.userActor(userId);
        return a != null && a.getName() != null ? a.getName() : "Người dùng";
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
            case TEXT, SYSTEM -> content != null ? content : "";
        };
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
