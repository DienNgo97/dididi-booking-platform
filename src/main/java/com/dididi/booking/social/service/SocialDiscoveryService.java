package com.dididi.booking.social.service;

import com.dididi.booking.social.api.dto.ActorView;
import com.dididi.booking.social.api.dto.UserCardView;
import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tim kiem & goi y nguoi dung de ket noi (trang "Moi nguoi"). */
@Service
@Transactional(readOnly = true)
public class SocialDiscoveryService {

    private final SocialProfileRepository profileRepository;
    private final FollowService followService;
    private final SocialActorService actorService;

    public SocialDiscoveryService(SocialProfileRepository profileRepository, FollowService followService,
                                  SocialActorService actorService) {
        this.profileRepository = profileRepository;
        this.followService = followService;
        this.actorService = actorService;
    }

    /** q rong/null -> goi y nguoi de theo doi; nguoc lai -> tim theo handle hoac ten hien thi. */
    public List<UserCardView> search(Long meId, String q, int limit) {
        if (q == null || q.isBlank()) {
            return suggest(meId, limit);
        }
        String term = q.trim();
        if (term.startsWith("@")) {
            term = term.substring(1);
        }
        if (term.isBlank()) {
            return suggest(meId, limit);
        }
        List<UserCardView> out = new ArrayList<>();
        for (SocialProfile p : profileRepository
                .findTop10ByHandleContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(term, term)) {
            if (p.getUserId() != null) {
                out.add(toCard(meId, p));
            }
        }
        return out;
    }

    /** Goi y nguoi minh chua theo doi (loai ban than & nguoi da follow ACTIVE), uu tien nhieu follower. */
    public List<UserCardView> suggest(Long meId, int limit) {
        Set<Long> exclude = new HashSet<>();
        exclude.add(meId);
        for (Follow f : followService.activeFollowsOf(meId)) {
            if (f.getFolloweeType() == ActorType.USER) {
                exclude.add(f.getFolloweeId());
            }
        }
        List<SocialProfile> candidates = new ArrayList<>();
        for (SocialProfile p : profileRepository.findAll()) {
            if (p.getUserId() != null && !exclude.contains(p.getUserId())) {
                candidates.add(p);
            }
        }
        candidates.sort(Comparator
                .comparingInt(SocialProfile::getFollowersCount).reversed()
                .thenComparing(SocialProfile::getUserId, Comparator.reverseOrder()));
        List<UserCardView> out = new ArrayList<>();
        for (SocialProfile p : candidates) {
            out.add(toCard(meId, p));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private UserCardView toCard(Long meId, SocialProfile p) {
        Long uid = p.getUserId();
        ActorView actor = actorService.userActor(uid);
        String state = followService.state(meId, ActorType.USER, uid);
        return new UserCardView(uid, actor, p.getBio(), state, "SELF".equals(state));
    }
}
