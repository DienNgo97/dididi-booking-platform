package com.dididi.booking.social.web;

import com.dididi.booking.common.i18n.I18nSupport;

import com.dididi.booking.social.domain.entity.Conversation;
import com.dididi.booking.social.domain.entity.Message;
import com.dididi.booking.social.service.MessagingService;
import com.dididi.booking.social.service.PostService;
import com.dididi.booking.social.service.SocialActorService;
import com.dididi.booking.social.service.SocialDiscoveryService;
import com.dididi.booking.social.service.SocialViewService;
import com.dididi.booking.storage.StorageService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.List;

/** Web khach (Thymeleaf + AJAX/polling) cho Tin nhắn (DM). */
@Controller
public class MessagingWebController {

    private final CurrentUser currentUser;
    private final MessagingService messagingService;
    private final SocialViewService viewService;
    private final SocialActorService actorService;
    private final PostService postService;
    private final SocialDiscoveryService discoveryService;

    public MessagingWebController(CurrentUser currentUser, MessagingService messagingService,
                                  SocialViewService viewService, SocialActorService actorService,
                                  PostService postService, SocialDiscoveryService discoveryService) {
        this.currentUser = currentUser;
        this.messagingService = messagingService;
        this.viewService = viewService;
        this.actorService = actorService;
        this.postService = postService;
        this.discoveryService = discoveryService;
    }

    @GetMapping("/community/messages")
    public String inbox(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("conversations", messagingService.inbox(uid));
        model.addAttribute("archivedCount", messagingService.archivedInbox(uid).size());
        model.addAttribute("archivedMode", false);
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("activeTab", "messages");
        return "social/messages";
    }

    /** Mục Lưu trữ: các cuộc trò chuyện đã cất đi, dùng chung template với hộp thư. */
    @GetMapping("/community/messages/archived")
    public String archived(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("conversations", messagingService.archivedInbox(uid));
        model.addAttribute("archivedCount", 0);
        model.addAttribute("archivedMode", true);
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("activeTab", "messages");
        return "social/messages";
    }

    @GetMapping("/community/messages/{convId}")
    public String thread(@PathVariable Long convId, Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        messagingService.requireConversation(convId, uid);
        List<Message> msgs = messagingService.messages(convId, uid);
        List<com.dididi.booking.social.api.dto.MessageView> views = viewMessages(msgs, uid);
        // Mở một hội thoại ĐANG lưu trữ thì cột trái phải là danh sách Lưu trữ, nếu không hàng đang
        // xem biến mất khỏi danh sách và người dùng mất luôn lối quay lại mục đó.
        boolean archived = messagingService.isArchived(convId, uid);
        model.addAttribute("conversations", archived ? messagingService.archivedInbox(uid) : messagingService.inbox(uid));
        model.addAttribute("archivedCount", archived ? 0 : messagingService.archivedInbox(uid).size());
        model.addAttribute("archivedMode", archived);
        model.addAttribute("convId", convId);
        model.addAttribute("other", messagingService.otherActor(convId, uid));
        model.addAttribute("messages", views);
        model.addAttribute("lastId", views.isEmpty() ? 0L : views.get(views.size() - 1).getId());
        model.addAttribute("me", actorService.userActor(uid));
        boolean group = messagingService.isGroup(convId);
        model.addAttribute("group", group);
        model.addAttribute("owner", group && messagingService.isOwner(convId, uid));
        model.addAttribute("members", group ? messagingService.members(convId, uid) : List.of());
        model.addAttribute("activeTab", "messages");
        return "social/thread";
    }

    // ----- xoá / lưu trữ cuộc trò chuyện (chỉ tác động phía người bấm) -----

    @PostMapping("/community/messages/{convId}/delete")
    public String delete(@PathVariable Long convId, Authentication auth, RedirectAttributes ra) {
        messagingService.deleteConversation(currentUser.id(auth), convId);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f51",
                "Đã xoá cuộc trò chuyện khỏi hộp thư của bạn."));
        return "redirect:/community/messages";
    }

    @PostMapping("/community/messages/{convId}/archive")
    public String archive(@PathVariable Long convId, @RequestParam(defaultValue = "true") boolean on,
                          Authentication auth, RedirectAttributes ra) {
        messagingService.archiveConversation(currentUser.id(auth), convId, on);
        ra.addFlashAttribute("message", on
                ? I18nSupport.msg("flash.f52", "Đã chuyển vào mục Lưu trữ.")
                : I18nSupport.msg("flash.f53", "Đã đưa lại về hộp thư."));
        return on ? "redirect:/community/messages" : "redirect:/community/messages/" + convId;
    }

    // ----- nhóm chat -----

    /** Form tạo nhóm: gợi ý người để mời + ô tìm. Cùng template dùng lại cho màn thêm thành viên. */
    @GetMapping("/community/messages/group/new")
    public String groupForm(@RequestParam(required = false) String q, Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("people", discoveryService.search(uid, q, 24));
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("convId", null);
        model.addAttribute("activeTab", "messages");
        return "social/group-new";
    }

    /** Màn thêm thành viên cho nhóm đang mở — mọi thành viên đều vào được. */
    @GetMapping("/community/messages/{convId}/add")
    public String addMemberForm(@PathVariable Long convId, @RequestParam(required = false) String q,
                                Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        messagingService.requireConversation(convId, uid);
        if (!messagingService.isGroup(convId)) {
            return "redirect:/community/messages/" + convId;   // hội thoại 1-1 không có thành viên để thêm
        }
        model.addAttribute("people", discoveryService.search(uid, q, 24));
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("convId", convId);
        model.addAttribute("groupName", messagingService.otherActor(convId, uid).getName());
        model.addAttribute("activeTab", "messages");
        return "social/group-new";
    }

    @PostMapping("/community/messages/group")
    public String createGroup(@RequestParam String title,
                              @RequestParam(name = "memberIds", required = false) List<Long> memberIds,
                              Authentication auth, RedirectAttributes ra) {
        Conversation c = messagingService.createGroup(currentUser.id(auth), title, memberIds);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f54", "Đã tạo nhóm chat."));
        return "redirect:/community/messages/" + c.getId();
    }

    /** Thêm thành viên — mọi thành viên trong nhóm đều làm được. */
    @PostMapping("/community/messages/{convId}/members")
    public String addMembers(@PathVariable Long convId,
                             @RequestParam(name = "memberIds", required = false) List<Long> memberIds,
                             Authentication auth, RedirectAttributes ra) {
        messagingService.addMembers(currentUser.id(auth), convId, memberIds);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f55", "Đã cập nhật thành viên nhóm."));
        return "redirect:/community/messages/" + convId;
    }

    @PostMapping("/community/messages/{convId}/members/remove")
    public String removeMember(@PathVariable Long convId, @RequestParam Long userId,
                               Authentication auth, RedirectAttributes ra) {
        messagingService.removeMember(currentUser.id(auth), convId, userId);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f56", "Đã xoá thành viên khỏi nhóm."));
        return "redirect:/community/messages/" + convId;
    }

    @PostMapping("/community/messages/{convId}/rename")
    public String rename(@PathVariable Long convId, @RequestParam String title,
                         Authentication auth, RedirectAttributes ra) {
        messagingService.renameGroup(currentUser.id(auth), convId, title);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f57", "Đã đổi tên nhóm."));
        return "redirect:/community/messages/" + convId;
    }

    @PostMapping("/community/messages/{convId}/leave")
    public String leave(@PathVariable Long convId, Authentication auth, RedirectAttributes ra) {
        messagingService.leaveGroup(currentUser.id(auth), convId);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f58", "Bạn đã rời nhóm."));
        return "redirect:/community/messages";
    }

    /** Tìm người để thêm vào nhóm (dùng lại kết quả tìm kiếm dạng fragment). */
    @GetMapping("/community/messages/people-search")
    public String peopleSearch(@RequestParam(required = false) String q, Authentication auth, Model model) {
        model.addAttribute("people", discoveryService.search(currentUser.id(auth), q, 24));
        return "social/group-new :: picker";
    }

    @PostMapping("/community/messages/start")
    public String start(@RequestParam Long toUserId, Authentication auth, RedirectAttributes ra) {
        Long uid = currentUser.id(auth);
        Conversation c = messagingService.getOrCreateDirect(uid, toUserId);
        return "redirect:/community/messages/" + c.getId();
    }

    /** Gửi tin (text hoặc ảnh) — trả về HTML 1 bong bóng tin để JS chèn vào. */
    @PostMapping("/community/messages/{convId}/send")
    public String send(@PathVariable Long convId, @RequestParam(required = false) String content,
                       @RequestParam(required = false) MultipartFile image, Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        Message m;
        if (image != null && !image.isEmpty()) {
            m = messagingService.sendImage(uid, convId, image);
        } else {
            m = messagingService.sendText(uid, convId, content);
        }
        model.addAttribute("m", viewMessages(List.of(m), uid).get(0));
        return "social/dm-fragments :: message";
    }

    /** Polling: tin mới hơn afterId. */
    @GetMapping("/community/messages/{convId}/poll")
    public String poll(@PathVariable Long convId, @RequestParam(defaultValue = "0") long afterId,
                       Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("messages", viewMessages(messagingService.newMessages(convId, uid, afterId), uid));
        return "social/dm-fragments :: messages";
    }

    // ----- chia sẻ bài qua DM -----

    @GetMapping("/community/messages/share")
    public String shareForm(@RequestParam Long postId, Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("post", viewService.toPostView(postService.getForView(uid, postId), uid, false));
        model.addAttribute("conversations", messagingService.inbox(uid));
        model.addAttribute("me", actorService.userActor(uid));
        return "social/share";
    }

    @PostMapping("/community/messages/share")
    public String share(@RequestParam Long convId, @RequestParam Long postId,
                        @RequestParam(required = false) String text, Authentication auth, RedirectAttributes ra) {
        Long uid = currentUser.id(auth);
        messagingService.sharePost(uid, convId, postId, text);
        ra.addFlashAttribute("message", I18nSupport.msg("flash.f21", "Đã gửi bài viết qua tin nhắn."));
        return "redirect:/community/messages/" + convId;
    }

    // ----- serve ảnh trong tin nhắn (kiểm tra quyền) -----

    @GetMapping("/community/messages/media/{messageId}")
    public ResponseEntity<byte[]> media(@PathVariable Long messageId, Authentication auth) {
        StorageService.StoredObject obj = messagingService.loadImage(messageId, currentUser.id(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.safeContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(obj.bytes());
    }

    private List<com.dididi.booking.social.api.dto.MessageView> viewMessages(List<Message> msgs, Long uid) {
        return messagingService.toMessageViews(msgs, uid);
    }
}
