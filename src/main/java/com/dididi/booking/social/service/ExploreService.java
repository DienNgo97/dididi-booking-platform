package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Hashtag;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Kham pha: bai trending (diem tuong tac co giam dan theo thoi gian) + hashtag hot. */
@Service
@Transactional(readOnly = true)
public class ExploreService {

    private static final int CANDIDATES = 200;

    private final PostRepository postRepository;
    private final HashtagService hashtagService;

    public ExploreService(PostRepository postRepository, HashtagService hashtagService) {
        this.postRepository = postRepository;
        this.hashtagService = hashtagService;
    }

    public List<Post> trendingPosts(int limit) {
        List<Post> recent = new ArrayList<>(
                postRepository.explore(PostStatus.PUBLISHED, Long.MAX_VALUE, PageRequest.of(0, CANDIDATES)));
        long now = System.currentTimeMillis();
        recent.sort((a, b) -> Double.compare(score(b, now), score(a, now)));
        return recent.size() > limit ? new ArrayList<>(recent.subList(0, limit)) : recent;
    }

    public List<Hashtag> trendingHashtags() {
        return hashtagService.trending();
    }

    private double score(Post p, long now) {
        double engagement = p.getLikeCount() + 2.0 * p.getCommentCount() + 3.0 * p.getRepostCount();
        long ageHours = p.getCreatedAt() != null
                ? Math.max(0, (now - p.getCreatedAt().toEpochMilli()) / 3_600_000L) : 0;
        return (engagement + 1.0) / Math.pow(ageHours + 2.0, 1.5);
    }
}
