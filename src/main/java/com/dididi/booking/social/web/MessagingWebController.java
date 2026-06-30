package com.dididi.booking.social.web;

import com.dididi.booking.social.domain.entity.Conversation;
import com.dididi.booking.social.domain.entity.Message;
import com.dididi.booking.social.service.MessagingService;
import com.dididi.booking.social.service.PostService;
import com.dididi.booking.social.service.SocialActorService;
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

    public MessagingWebController(CurrentUser currentUser, MessagingService messagingService,
                                  SocialViewService viewService, SocialActorService actorService,
                                  PostService postService) {
        this.currentUser = currentUser;
        this.messagingService = messagingService;
        this.viewService = viewService;
        this.actorService = actorService;
        this.postService = postService;
    }

    @GetMapping("/community/messages")
    public String inbox(Authentication auth, Model model) {
        Long uid = currentUser.id(auth);
        model.addAttribute("conversations", messagingService.inbox(uid));
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
        model.addAttribute("conversations", messagingService.inbox(uid));
        model.addAttribute("convId", convId);
        model.addAttribute("other", messagingService.otherActor(convId, uid));
        model.addAttribute("messages", views);
        model.addAttribute("lastId", views.isEmpty() ? 0L : views.get(views.size() - 1).getId());
        model.addAttribute("me", actorService.userActor(uid));
        model.addAttribute("activeTab", "messages");
        return "social/thread";
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
        ra.addFlashAttribute("message", "Đã gửi bài viết qua tin nhắn.");
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
