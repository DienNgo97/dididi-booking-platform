package com.dididi.booking.social.service;

import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.ReactionTarget;
import com.dididi.booking.social.domain.enums.ReportReason;
import com.dididi.booking.social.domain.enums.ReportStatus;
import com.dididi.booking.social.repository.CommentRepository;
import com.dididi.booking.social.repository.ContentReportRepository;
import com.dididi.booking.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Bao cao noi dung + tu an khi vuot nguong. */
@Service
@Transactional
public class ReportService {

    private static final long AUTO_HIDE_THRESHOLD = 5;

    private final ContentReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;

    public ReportService(ContentReportRepository reportRepository, PostRepository postRepository,
                         CommentRepository commentRepository, PostService postService) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postService = postService;
    }

    /** Gui bao cao (bo qua neu user da bao cao doi tuong nay). */
    public void submit(Long reporterUserId, ReactionTarget targetType, Long targetId, ReportReason reason, String note) {
        // Chi cho bao cao noi dung nguoi dung duoc phep xem (chong report-DoS len bai khong xem duoc).
        Long postId = (targetType == ReactionTarget.POST) ? targetId
                : commentRepository.findById(targetId).map(com.dididi.booking.social.domain.entity.Comment::getPostId).orElse(null);
        if (postId != null) {
            postService.getForView(reporterUserId, postId);
        }
        if (reportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(reporterUserId, targetType, targetId)) {
            return;
        }
        ContentReport r = new ContentReport();
        r.setReporterUserId(reporterUserId);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReason(reason);
        r.setNote(note != null && note.length() > 500 ? note.substring(0, 500) : note);
        r.setStatus(ReportStatus.OPEN);
        reportRepository.save(r);

        long open = reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.OPEN);
        if (open >= AUTO_HIDE_THRESHOLD) {
            autoHide(targetType, targetId);
        }
    }

    private void autoHide(ReactionTarget targetType, Long targetId) {
        if (targetType == ReactionTarget.POST) {
            postRepository.findById(targetId).ifPresent(p -> {
                if (p.getStatus() == PostStatus.PUBLISHED) {
                    p.setStatus(PostStatus.HIDDEN);
                    postRepository.save(p);
                }
            });
        } else {
            commentRepository.findById(targetId).ifPresent(c -> {
                if (c.getStatus() == PostStatus.PUBLISHED) {
                    c.setStatus(PostStatus.HIDDEN);
                    commentRepository.saveAndFlush(c);
                    // dem lai commentCount cua bai (comment bi an khong con duoc tinh) — DI-B: UPDATE nguyen tu
                    postRepository.updateCommentCount(c.getPostId(),
                            (int) commentRepository.countByPostIdAndStatus(c.getPostId(), PostStatus.PUBLISHED));
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<ContentReport> openReports(int limit) {
        return reportRepository.findByStatusOrderByIdDesc(ReportStatus.OPEN, PageRequest.of(0, limit));
    }
}
