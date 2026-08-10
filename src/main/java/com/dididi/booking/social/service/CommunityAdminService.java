package com.dididi.booking.social.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.entity.Hashtag;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportStatus;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.ContentReportRepository;
import com.dididi.booking.social.repository.HashtagRepository;
import com.dididi.booking.social.repository.PostRepository;
import com.dididi.booking.social.repository.SocialProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ KIỂM DUYỆT CỘNG ĐỒNG cho admin: quản lý báo cáo, bài viết, bình luận, thành viên + thống kê.
 *
 * Tái dùng {@link PostService#deletePost}/{@link CommentService#delete} (đã hỗ trợ cờ isAdmin) cho thao tác GỠ,
 * và tự thao tác trạng thái PUBLISHED/HIDDEN cho ẨN/HIỆN. Nội dung đã GỠ (REMOVED + soft-delete) chỉ thấy/khôi
 * phục được qua native query trong repository (vì @SQLRestriction("deleted_at IS NULL") ẩn khỏi query thường).
 *
 * KHÔNG ghi AuditEvent ở đây — controller ({@code AdminCommunityApiController}) publish audit sau mỗi thao tác
 * (đồng nhất với AdminReviewApiController / AdminUserApiController).
 */
@Service
@Transactional
public class CommunityAdminService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentReportRepository reportRepository;
    private final SocialProfileRepository profileRepository;
    private final HashtagRepository hashtagRepository;
    private final HashtagService hashtagService;
    private final PostService postService;
    private final CommentService commentService;
    private final UserRepository userRepository;

    public CommunityAdminService(PostRepository postRepository, CommentRepository commentRepository,
                                 ContentReportRepository reportRepository, SocialProfileRepository profileRepository,
                                 HashtagRepository hashtagRepository, HashtagService hashtagService,
                                 PostService postService, CommentService commentService,
                                 UserRepository userRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.profileRepository = profileRepository;
        this.hashtagRepository = hashtagRepository;
        this.hashtagService = hashtagService;
        this.postService = postService;
        this.commentService = commentService;
        this.userRepository = userRepository;
    }

    // ===================== DASHBOARD =====================

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("postsByStatus", countMap(postRepository.adminCountByStatus()));
        out.put("commentsByStatus", countMap(commentRepository.adminCountByStatus()));
        out.put("members", profileRepository.count());

        Map<String, Long> reports = new LinkedHashMap<>();
        for (ReportStatus s : ReportStatus.values()) {
            reports.put(s.name(), reportRepository.countByStatus(s));
        }
        out.put("reports", reports);

        List<Map<String, Object>> tags = hashtagRepository
                .findTop10ByPostCountGreaterThanOrderByPostCountDesc(0).stream()
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tag", h.getTag());
                    m.put("postCount", h.getPostCount());
                    return m;
                })
                .toList();
        out.put("topHashtags", tags);
        return out;
    }

    private static Map<String, Long> countMap(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        // đảm bảo đủ 3 trạng thái kể cả khi = 0
        for (PostStatus s : PostStatus.values()) m.put(s.name(), 0L);
        for (Object[] r : rows) {
            if (r != null && r.length >= 2 && r[0] != null) {
                m.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
            }
        }
        return m;
    }

    // ===================== REPORTS =====================

    @Transactional(readOnly = true)
    public Page<ContentReport> listReports(ReportStatus status, int page, int size) {
        Pageable pg = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return (status == null) ? reportRepository.findAll(pg) : reportRepository.findByStatus(status, pg);
    }

    /**
     * Ẩn nội dung bị báo cáo + đóng mọi báo cáo OPEN của cùng đối tượng thành ACTIONED.
     * Nội dung đã bị GỠ trước đó (tác giả tự xoá / admin khác xử lý) -> không ném 404,
     * vẫn đóng báo cáo ACTIONED (thực tế nội dung đã không còn hiển thị).
     */
    public void reportHide(Long reportId, Long adminId) {
        ContentReport r = getReport(reportId);
        try {
            if (r.getTargetType() == ReactionTarget.POST) hidePost(r.getTargetId());
            else hideComment(r.getTargetId());
        } catch (BusinessException e) {
            if (!isNotFound(e)) throw e; // nội dung đã REMOVED -> coi như đã xử lý
        }
        resolveTarget(r.getTargetType(), r.getTargetId(), ReportStatus.ACTIONED, adminId);
    }

    /** Gỡ hẳn nội dung bị báo cáo (soft delete) + đóng báo cáo ACTIONED. Nội dung đã gỡ sẵn -> chỉ đóng báo cáo. */
    public void reportRemove(Long reportId, Long adminId) {
        ContentReport r = getReport(reportId);
        try {
            if (r.getTargetType() == ReactionTarget.POST) postService.deletePost(adminId, true, r.getTargetId());
            else commentService.delete(adminId, true, r.getTargetId());
        } catch (BusinessException e) {
            if (!isNotFound(e)) throw e; // nội dung đã REMOVED -> coi như đã xử lý
        }
        resolveTarget(r.getTargetType(), r.getTargetId(), ReportStatus.ACTIONED, adminId);
    }

    /** BusinessException dạng "không tìm thấy" (nội dung đã bị soft-delete, @SQLRestriction ẩn khỏi findById). */
    private static boolean isNotFound(BusinessException e) {
        return e.getStatus() == HttpStatus.NOT_FOUND;
    }

    /** Bỏ qua báo cáo (giữ nguyên nội dung) + đóng báo cáo DISMISSED. */
    public void reportDismiss(Long reportId, Long adminId) {
        ContentReport r = getReport(reportId);
        resolveTarget(r.getTargetType(), r.getTargetId(), ReportStatus.DISMISSED, adminId);
    }

    private void resolveTarget(ReactionTarget type, Long targetId, ReportStatus newStatus, Long adminId) {
        for (ContentReport rep : reportRepository.findByTargetTypeAndTargetIdAndStatus(type, targetId, ReportStatus.OPEN)) {
            rep.setStatus(newStatus);
            rep.setHandledByUserId(adminId);
            reportRepository.save(rep);
        }
    }

    private ContentReport getReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> notFound("REPORT_NOT_FOUND", "báo cáo"));
    }

    // ===================== POSTS =====================

    @Transactional(readOnly = true)
    public Page<Post> listPosts(PostStatus status, Long authorId, String q, int page, int size) {
        return postRepository.adminSearch(
                status == null ? null : status.name(),
                authorId,
                (q == null || q.isBlank()) ? null : q.trim(),
                PageRequest.of(page, size));
    }

    public Post hidePost(Long id) { return setPostStatus(id, PostStatus.HIDDEN); }

    public Post unhidePost(Long id) { return setPostStatus(id, PostStatus.PUBLISHED); }

    private Post setPostStatus(Long id, PostStatus st) {
        Post p = postRepository.findById(id).orElseThrow(() -> notFound("POST_NOT_FOUND", "bài viết"));
        p.setStatus(st);
        return postRepository.save(p);
    }

    public void removePost(Long id, Long adminId) {
        postService.deletePost(adminId, true, id);
    }

    /** Khôi phục bài đã gỡ (native, bỏ qua @SQLRestriction) + hoàn lại các side-effect của deletePost. */
    public void restorePost(Long id) {
        if (postRepository.adminRestore(id) == 0) throw notFound("POST_NOT_FOUND", "bài viết");
        // Sau UPDATE native, deleted_at=NULL nên findById thấy lại bài -> hoàn tác những gì deletePost đã dọn:
        // re-link hashtag từ caption (deletePost đã unlink CỨNG: xoá PostHashtag + giảm Hashtag.postCount).
        postRepository.findById(id).ifPresent(p -> hashtagService.linkHashtags(p.getId(), p.getCaption()));
    }

    // ===================== COMMENTS =====================

    @Transactional(readOnly = true)
    public Page<Comment> listComments(PostStatus status, Long authorId, Long postId, int page, int size) {
        return commentRepository.adminSearch(
                status == null ? null : status.name(), authorId, postId, PageRequest.of(page, size));
    }

    public Comment hideComment(Long id) { return setCommentStatus(id, PostStatus.HIDDEN); }

    public Comment unhideComment(Long id) { return setCommentStatus(id, PostStatus.PUBLISHED); }

    private Comment setCommentStatus(Long id, PostStatus st) {
        Comment c = commentRepository.findById(id).orElseThrow(() -> notFound("COMMENT_NOT_FOUND", "bình luận"));
        c.setStatus(st);
        commentRepository.saveAndFlush(c);
        // đồng bộ commentCount của bài (chỉ đếm PUBLISHED) — DI-B: UPDATE nguyên tử
        postRepository.updateCommentCount(c.getPostId(),
                (int) commentRepository.countByPostIdAndStatus(c.getPostId(), PostStatus.PUBLISHED));
        return c;
    }

    public void removeComment(Long id, Long adminId) {
        commentService.delete(adminId, true, id);
    }

    public void restoreComment(Long id) {
        if (commentRepository.adminRestore(id) == 0) throw notFound("COMMENT_NOT_FOUND", "bình luận");
        // Sau UPDATE native, comment hiện lại -> đếm lại commentCount của bài (mọi nhánh khác đều recount).
        commentRepository.findById(id).ifPresent(c ->
                postRepository.updateCommentCount(c.getPostId(),
                        (int) commentRepository.countByPostIdAndStatus(c.getPostId(), PostStatus.PUBLISHED)));
    }

    // ===================== MEMBERS =====================

    @Transactional(readOnly = true)
    public Page<SocialProfile> listMembers(String q, int page, int size) {
        Pageable pg = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return (q == null || q.isBlank())
                ? profileRepository.findAll(pg)
                : profileRepository.findByHandleContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(q.trim(), q.trim(), pg);
    }

    // ===================== TIỆN ÍCH =====================

    /** Map userId -> tên hiển thị (fullName, fallback email) để làm giàu DTO mà không N+1. */
    @Transactional(readOnly = true)
    public Map<Long, String> userNames(Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) return m;
        for (User u : userRepository.findAllById(ids)) {
            m.put(u.getId(), (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getEmail());
        }
        return m;
    }

    /** Map userId -> User (cho danh sách thành viên: role/status/email). */
    @Transactional(readOnly = true)
    public Map<Long, User> usersById(Collection<Long> ids) {
        Map<Long, User> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) return m;
        for (User u : userRepository.findAllById(ids)) m.put(u.getId(), u);
        return m;
    }

    private static BusinessException notFound(String code, String what) {
        return new BusinessException(code, "Không tìm thấy " + what, HttpStatus.NOT_FOUND);
    }
}
