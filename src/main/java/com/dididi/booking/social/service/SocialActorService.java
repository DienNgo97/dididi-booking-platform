package com.dididi.booking.social.service;

import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.api.dto.ActorView;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Do thong tin hien thi cho chu the (USER/HOTEL), ho tro batch de tranh N+1. */
@Service
@Transactional(readOnly = true)
public class SocialActorService {

    private final SocialProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;

    public SocialActorService(SocialProfileRepository profileRepository, UserRepository userRepository,
                              HotelRepository hotelRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
    }

    public ActorView userActor(Long userId) {
        SocialProfile p = profileRepository.findByUserId(userId).orElse(null);
        User u = userRepository.findById(userId).orElse(null);
        return buildUserActor(userId, p, u);
    }

    public ActorView hotelActor(Long hotelId) {
        Hotel h = hotelRepository.findById(hotelId).orElse(null);
        return buildHotelActor(hotelId, h);
    }

    public Map<Long, ActorView> batchUserActors(Collection<Long> userIds) {
        Map<Long, ActorView> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        Map<Long, SocialProfile> profById = profileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(SocialProfile::getUserId, Function.identity(), (a, b) -> a));
        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        for (Long id : userIds) {
            out.put(id, buildUserActor(id, profById.get(id), userById.get(id)));
        }
        return out;
    }

    public Map<Long, ActorView> batchHotelActors(Collection<Long> hotelIds) {
        Map<Long, ActorView> out = new HashMap<>();
        if (hotelIds == null || hotelIds.isEmpty()) {
            return out;
        }
        for (Hotel h : hotelRepository.findAllById(hotelIds)) {
            out.put(h.getId(), buildHotelActor(h.getId(), h));
        }
        for (Long id : hotelIds) {
            out.computeIfAbsent(id, k -> buildHotelActor(k, null));
        }
        return out;
    }

    private ActorView buildUserActor(Long id, SocialProfile p, User u) {
        String handle = p != null ? p.getHandle() : "user" + id;
        String name;
        if (p != null && p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
            name = p.getDisplayName();
        } else if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
            name = u.getFullName();
        } else {
            name = "Thành viên Dididi";
        }
        String avatarUrl = p != null ? SocialProfileService.avatarUrl(id, p.getAvatarKey()) : null;
        return new ActorView("USER", id, name, handle, avatarUrl, "/community/u/" + handle);
    }

    private ActorView buildHotelActor(Long id, Hotel h) {
        String name = h != null ? h.getName() : "Khách sạn";
        return new ActorView("HOTEL", id, name, null, null, "/community/hotel/" + id);
    }

    public Optional<Hotel> hotel(Long hotelId) {
        return hotelRepository.findById(hotelId);
    }
}
