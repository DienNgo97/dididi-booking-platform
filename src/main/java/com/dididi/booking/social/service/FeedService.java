package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Follow;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.ActorType;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.PostVisibility;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Feed ca nhan hoa (nguoi/khach san minh theo doi + cua minh) + Kham pha. */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private final PostRepository postRepository;
    private final FollowService followService;

    public FeedService(PostRepository postRepository, FollowService followService) {
        this.postRepository = postRepository;
        this.followService = followService;
    }

    public List<Post> feed(Long viewerUserId, long cursor, int size) {
        List<Long> userIds = new ArrayList<>();
        userIds.add(viewerUserId);
        List<Long> hotelIds = new ArrayList<>();
        for (Follow f : followService.activeFollowsOf(viewerUserId)) {
            if (f.getFolloweeType() == ActorType.USER) {
                userIds.add(f.getFolloweeId());
            } else {
                hotelIds.add(f.getFolloweeId());
            }
        }
        if (hotelIds.isEmpty()) {
            hotelIds.add(-1L);
        }
        long cur = cursor > 0 ? cursor : Long.MAX_VALUE;
        return postRepository.feed(PostStatus.PUBLISHED, PostVisibility.PRIVATE, viewerUserId,
                ActorType.USER, ActorType.HOTEL, userIds, hotelIds, cur, PageRequest.of(0, size));
    }

    public List<Post> explore(long cursor, int size) {
        long cur = cursor > 0 ? cursor : Long.MAX_VALUE;
        return postRepository.explore(PostStatus.PUBLISHED, cur, PageRequest.of(0, size));
    }
}
