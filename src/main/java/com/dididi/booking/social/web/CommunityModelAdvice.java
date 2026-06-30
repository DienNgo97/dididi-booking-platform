package com.dididi.booking.social.web;

import com.dididi.booking.social.service.MessagingService;
import com.dididi.booking.social.service.NotificationService;
import com.dididi.booking.web.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Cap so chua doc (thong bao + tin nhan) cho moi trang Cong dong + Tin nhan -> hien badge. */
@ControllerAdvice(assignableTypes = {CommunityWebController.class, MessagingWebController.class})
public class CommunityModelAdvice {

    private final CurrentUser currentUser;
    private final NotificationService notificationService;
    private final MessagingService messagingService;

    public CommunityModelAdvice(CurrentUser currentUser, NotificationService notificationService,
                                MessagingService messagingService) {
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.messagingService = messagingService;
    }

    @ModelAttribute("communityUnread")
    public long communityUnread(Authentication auth) {
        Long uid = currentUser.idOrNull(auth);
        return uid == null ? 0 : notificationService.unreadCount(uid);
    }

    @ModelAttribute("communityDmUnread")
    public long communityDmUnread(Authentication auth) {
        Long uid = currentUser.idOrNull(auth);
        return uid == null ? 0 : messagingService.dmUnreadTotal(uid);
    }
}
