package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Hashtag;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.PostHashtag;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.repository.HashtagRepository;
import com.dididi.booking.social.repository.PostHashtagRepository;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tach #hashtag tu caption, gan vao bai, dem so bai (trending), liet ke bai theo hashtag. */
@Service
@Transactional
public class HashtagService {

    private static final Pattern TAG = Pattern.compile("#([\\p{L}0-9_]+)");

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostRepository postRepository;

    public HashtagService(HashtagRepository hashtagRepository, PostHashtagRepository postHashtagRepository,
                          PostRepository postRepository) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.postRepository = postRepository;
    }

    public void linkHashtags(Long postId, String caption) {
        if (caption == null || caption.isBlank()) {
            return;
        }
        for (String tag : extract(caption)) {
            Hashtag h = hashtagRepository.findByTag(tag).orElseGet(() -> {
                Hashtag x = new Hashtag();
                x.setTag(tag);
                return hashtagRepository.save(x);
            });
            if (!postHashtagRepository.existsByPostIdAndHashtagId(postId, h.getId())) {
                PostHashtag ph = new PostHashtag();
                ph.setPostId(postId);
                ph.setHashtagId(h.getId());
                postHashtagRepository.save(ph);
                h.setPostCount(h.getPostCount() + 1);
                hashtagRepository.save(h);
            }
        }
    }

    /** Go lien ket hashtag khi xoa bai: giam postCount + xoa PostHashtag (tranh trending phinh ao). */
    public void unlinkPost(Long postId) {
        for (PostHashtag ph : postHashtagRepository.findByPostId(postId)) {
            hashtagRepository.findById(ph.getHashtagId()).ifPresent(h -> {
                h.setPostCount(Math.max(0, h.getPostCount() - 1));
                hashtagRepository.save(h);
            });
            postHashtagRepository.delete(ph);
        }
    }

    public static Set<String> extract(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            String t = m.group(1).toLowerCase(Locale.ROOT);
            if (t.length() > 100) {
                t = t.substring(0, 100);
            }
            out.add(t);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<Hashtag> byTag(String tag) {
        return tag == null ? Optional.empty() : hashtagRepository.findByTag(tag.toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<Hashtag> trending() {
        return hashtagRepository.findTop10ByPostCountGreaterThanOrderByPostCountDesc(0);
    }

    /** Tim hashtag theo tu khoa (bo dau # neu co). */
    @Transactional(readOnly = true)
    public List<Hashtag> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String term = q.trim();
        if (term.startsWith("#")) {
            term = term.substring(1);
        }
        return term.isBlank() ? List.of()
                : hashtagRepository.findTop10ByTagContainingIgnoreCaseOrderByPostCountDesc(term);
    }

    @Transactional(readOnly = true)
    public List<Post> postsByTag(Long hashtagId, long cursor, int size) {
        long cur = cursor > 0 ? cursor : Long.MAX_VALUE;
        return postRepository.postsByHashtag(hashtagId, PostStatus.PUBLISHED, cur, PageRequest.of(0, size));
    }
}
