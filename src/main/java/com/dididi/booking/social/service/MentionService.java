package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.PostMention;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.repository.PostMentionRepository;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tach @handle, luu PostMention + sinh thong bao MENTION. */
@Service
@Transactional
public class MentionService {

    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_]+)");

    private final PostMentionRepository mentionRepository;
    private final SocialProfileRepository profileRepository;
    private final NotificationService notificationService;

    public MentionService(PostMentionRepository mentionRepository, SocialProfileRepository profileRepository,
                          NotificationService notificationService) {
        this.mentionRepository = mentionRepository;
        this.profileRepository = profileRepository;
        this.notificationService = notificationService;
    }

    public void process(Long postId, Long authorUserId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String handle : extract(text)) {
            profileRepository.findByHandle(handle).ifPresent(p -> {
                Long uid = p.getUserId();
                if (uid.equals(authorUserId)) {
                    return;
                }
                PostMention pm = new PostMention();
                pm.setPostId(postId);
                pm.setMentionedUserId(uid);
                mentionRepository.save(pm);
                notificationService.create(uid, authorUserId, NotificationType.MENTION, postId, null);
            });
        }
    }

    public static Set<String> extract(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(text);
        while (m.find()) {
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
