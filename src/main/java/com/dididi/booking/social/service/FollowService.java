package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.FollowStatus;
import com.dididi.booking.social.domain.enums.NotificationType;
import com.dididi.booking.social.repository.FollowRepository;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Theo doi / huy theo doi / duyet yeu cau (tai khoan rieng tu). */
@Service
@Transactional
public class FollowService {

    private final FollowRepository followRepository;
    private final SocialProfileRepository profileRepository;
    private final SocialProfileService profileService;
    private final HotelRepository hotelRepository;
    private final NotificationService notificationService;

    public FollowService(FollowRepository followRepository, SocialProfileRepository profileRepository,
                         SocialProfileService profileService, HotelRepository hotelRepository,
                         NotificationService notificationService) {
        this.followRepository = followRepository;
        this.profileRepository = profileRepository;
        this.profileService = profileService;
        this.hotelRepository = hotelRepository;
        this.notificationService = notificationService;
    }

    /** Theo doi 1 chu the. Tra ve trang thai (ACTIVE hoac PENDING neu user dich rieng tu). */
    public FollowStatus follow(Long followerUserId, ActorType type, Long id) {
        if (type == ActorType.USER && id.equals(followerUserId)) {
            throw new BusinessException("FOLLOW_SELF", "Không thể tự theo dõi chính mình", HttpStatus.BAD_REQUEST);
        }
        if (type == ActorType.HOTEL && hotelRepository.findById(id).isEmpty()) {
            throw new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND);
        }
        Follow existing = followRepository
                .findByFollowerUserIdAndFolloweeTypeAndFolloweeId(followerUserId, type, id).orElse(null);
        if (existing != null) {
            return existing.getStatus();
        }
        FollowStatus status = FollowStatus.ACTIVE;
        if (type == ActorType.USER) {
            boolean priv = profileRepository.findByUserId(id).map(SocialProfile::isPrivate).orElse(false);
            if (priv) {
                status = FollowStatus.PENDING;
            }
        }
        Follow f = new Follow();
        f.setFollowerUserId(followerUserId);
        f.setFolloweeType(type);
        f.setFolloweeId(id);
        f.setStatus(status);
        followRepository.save(f);
        recount(type, id, followerUserId);
        if (type == ActorType.USER) {
            notificationService.create(id, followerUserId,
                    status == FollowStatus.PENDING ? NotificationType.FOLLOW_REQUEST : NotificationType.FOLLOW, null, null);
        }
        return status;
    }

    public void unfollow(Long followerUserId, ActorType type, Long id) {
        followRepository.findByFollowerUserIdAndFolloweeTypeAndFolloweeId(followerUserId, type, id)
                .ifPresent(followRepository::delete);
        recount(type, id, followerUserId);
    }

    public void acceptRequest(Long ownerUserId, Long followId) {
        Follow f = requirePendingToOwner(ownerUserId, followId);
        f.setStatus(FollowStatus.ACTIVE);
        followRepository.save(f);
        recount(ActorType.USER, ownerUserId, f.getFollowerUserId());
        // Bao cho nguoi gui yeu cau biet da duoc chap nhan (giong Instagram)
        notificationService.create(f.getFollowerUserId(), ownerUserId,
                NotificationType.FOLLOW_ACCEPTED, null, null);
    }

    public void rejectRequest(Long ownerUserId, Long followId) {
        Follow f = requirePendingToOwner(ownerUserId, followId);
        followRepository.delete(f);
    }

    private Follow requirePendingToOwner(Long ownerUserId, Long followId) {
        Follow f = followRepository.findById(followId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy yêu cầu", HttpStatus.NOT_FOUND));
        if (f.getFolloweeType() != ActorType.USER || !f.getFolloweeId().equals(ownerUserId)
                || f.getStatus() != FollowStatus.PENDING) {
            throw new BusinessException("FORBIDDEN", "Không có quyền với yêu cầu này", HttpStatus.FORBIDDEN);
        }
        return f;
    }

    @Transactional(readOnly = true)
    public String state(Long followerUserId, ActorType type, Long id) {
        if (followerUserId == null) {
            return "NONE";
        }
        if (type == ActorType.USER && id.equals(followerUserId)) {
            return "SELF";
        }
        return followRepository.findByFollowerUserIdAndFolloweeTypeAndFolloweeId(followerUserId, type, id)
                .map(f -> f.getStatus() == FollowStatus.ACTIVE ? "ACTIVE" : "PENDING")
                .orElse("NONE");
    }

    @Transactional(readOnly = true)
    public boolean isActiveFollower(Long followerUserId, ActorType type, Long id) {
        return followerUserId != null && followRepository
                .existsByFollowerUserIdAndFolloweeTypeAndFolloweeIdAndStatus(followerUserId, type, id, FollowStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public long followersCount(ActorType type, Long id) {
        return followRepository.countByFolloweeTypeAndFolloweeIdAndStatus(type, id, FollowStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Follow> activeFollowsOf(Long followerUserId) {
        return followRepository.findByFollowerUserIdAndStatus(followerUserId, FollowStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Follow> pendingRequestsFor(Long ownerUserId) {
        return followRepository.findByFolloweeTypeAndFolloweeIdAndStatusOrderByIdDesc(
                ActorType.USER, ownerUserId, FollowStatus.PENDING);
    }

    private void recount(ActorType type, Long id, Long followerUserId) {
        if (type == ActorType.USER) {
            profileRepository.findByUserId(id).ifPresent(p -> {
                p.setFollowersCount((int) followRepository
                        .countByFolloweeTypeAndFolloweeIdAndStatus(ActorType.USER, id, FollowStatus.ACTIVE));
                profileRepository.save(p);
            });
        }
        profileService.getOrCreate(followerUserId);
        profileRepository.findByUserId(followerUserId).ifPresent(p -> {
            p.setFollowingCount((int) followRepository
                    .countByFollowerUserIdAndStatus(followerUserId, FollowStatus.ACTIVE));
            profileRepository.save(p);
        });
    }
}
