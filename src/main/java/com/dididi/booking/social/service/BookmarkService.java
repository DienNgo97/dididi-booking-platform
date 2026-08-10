package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.Bookmark;
import com.dididi.booking.social.repository.BookmarkRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Luu/bo luu bai + liet ke bai da luu. */
@Service
@Transactional
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostService postService;

    public BookmarkService(BookmarkRepository bookmarkRepository, PostService postService) {
        this.bookmarkRepository = bookmarkRepository;
        this.postService = postService;
    }

    public boolean toggle(Long userId, Long postId) {
        postService.getForView(userId, postId); // chi cho luu bai nguoi dung duoc phep xem
        // DI-B: toggle ở tầng DB — INSERT IGNORE/DELETE, bấm nhiều lần không ném lỗi.
        if (bookmarkRepository.existsByUserIdAndPostId(userId, postId)) {
            bookmarkRepository.deleteBookmark(userId, postId);
            return false;
        }
        bookmarkRepository.insertIgnore(userId, postId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> postIds(Long userId, int limit) {
        return bookmarkRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, limit))
                .stream().map(Bookmark::getPostId).toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> bookmarkedAmong(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return bookmarkRepository.findByUserIdAndPostIdIn(userId, postIds)
                .stream().map(Bookmark::getPostId).collect(Collectors.toSet());
    }
}
