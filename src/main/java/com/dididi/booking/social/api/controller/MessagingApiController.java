package com.dididi.booking.social.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.social.api.dto.ConversationView;
import com.dididi.booking.social.api.dto.MessageView;
import com.dididi.booking.social.domain.entity.Conversation;
import com.dididi.booking.social.domain.entity.Message;
import com.dididi.booking.social.service.MessagingService;
import com.dididi.booking.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** REST API Tin nhắn (DM) cho Flutter/Angular. JWT: principal = userId. */
@Tag(name = "Social - Messaging", description = "Tin nhắn 1-1 trong Cộng đồng")
@RestController
@RequestMapping("/api/v1/social")
public class MessagingApiController {

    private final MessagingService messagingService;

    public MessagingApiController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    @Operation(summary = "Hộp thư (danh sách hội thoại)")
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> inbox(Authentication auth) {
        return ApiResponse.ok(messagingService.inbox(uid(auth)));
    }

    @Operation(summary = "Mở/tạo hội thoại 1-1 với 1 người")
    @PostMapping("/conversations")
    public ApiResponse<Map<String, Object>> open(@RequestParam Long toUserId, Authentication auth) {
        Conversation c = messagingService.getOrCreateDirect(uid(auth), toUserId);
        return ApiResponse.ok(Map.of("conversationId", c.getId()));
    }

    @Operation(summary = "Tin nhắn trong hội thoại (cũ -> mới)")
    @GetMapping("/conversations/{convId}/messages")
    public ApiResponse<List<MessageView>> messages(@PathVariable Long convId, Authentication auth) {
        Long uid = uid(auth);
        return ApiResponse.ok(messagingService.toMessageViews(messagingService.messages(convId, uid), uid));
    }

    @Operation(summary = "Polling tin mới hơn afterId")
    @GetMapping("/conversations/{convId}/poll")
    public ApiResponse<List<MessageView>> poll(@PathVariable Long convId,
                                               @RequestParam(defaultValue = "0") long afterId, Authentication auth) {
        Long uid = uid(auth);
        return ApiResponse.ok(messagingService.toMessageViews(messagingService.newMessages(convId, uid, afterId), uid));
    }

    @Operation(summary = "Gửi tin (content text, hoặc multipart 'image')")
    @PostMapping("/conversations/{convId}/messages")
    public ApiResponse<MessageView> send(@PathVariable Long convId,
                                         @RequestParam(required = false) String content,
                                         @RequestPart(value = "image", required = false) MultipartFile image,
                                         Authentication auth) {
        Long uid = uid(auth);
        Message m = (image != null && !image.isEmpty())
                ? messagingService.sendImage(uid, convId, image)
                : messagingService.sendText(uid, convId, content);
        return ApiResponse.ok(messagingService.toMessageViews(List.of(m), uid).get(0), "Đã gửi");
    }

    @Operation(summary = "Chia sẻ 1 bài viết vào hội thoại")
    @PostMapping("/conversations/{convId}/share")
    public ApiResponse<MessageView> share(@PathVariable Long convId, @RequestParam Long postId,
                                          @RequestParam(required = false) String text, Authentication auth) {
        Long uid = uid(auth);
        Message m = messagingService.sharePost(uid, convId, postId, text);
        return ApiResponse.ok(messagingService.toMessageViews(List.of(m), uid).get(0), "Đã chia sẻ");
    }

    @Operation(summary = "Đánh dấu đã đọc hội thoại")
    @PostMapping("/conversations/{convId}/read")
    public ApiResponse<Void> read(@PathVariable Long convId, Authentication auth) {
        messagingService.markRead(convId, uid(auth));
        return ApiResponse.ok(null, "OK");
    }

    @Operation(summary = "Số tin nhắn chưa đọc")
    @GetMapping("/dm/unread-count")
    public ApiResponse<Map<String, Object>> unread(Authentication auth) {
        return ApiResponse.ok(Map.of("count", messagingService.dmUnreadTotal(uid(auth))));
    }

    @Operation(summary = "Tải ảnh trong tin nhắn (chỉ thành viên hội thoại)")
    @GetMapping("/conversations/{convId}/media/{messageId}")
    public ResponseEntity<byte[]> media(@PathVariable Long convId, @PathVariable Long messageId, Authentication auth) {
        StorageService.StoredObject obj = messagingService.loadImage(messageId, uid(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.safeContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(obj.bytes());
    }
}
