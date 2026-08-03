package com.dididi.booking.admin.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.dto.PagedResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.common.security.RoleUtils;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.social.api.dto.AdminSocialCommentDto;
import com.dididi.booking.social.api.dto.AdminSocialMemberDto;
import com.dididi.booking.social.api.dto.AdminSocialPostDto;
import com.dididi.booking.social.api.dto.AdminSocialReportDto;
import com.dididi.booking.social.domain.entity.Comment;
import com.dididi.booking.social.domain.entity.ContentReport;
import com.dididi.booking.social.domain.entity.Post;
import com.dididi.booking.social.domain.entity.SocialProfile;
import com.dididi.booking.social.domain.enums.PostStatus;
import com.dididi.booking.social.domain.enums.ReportStatus;
import com.dididi.booking.social.service.CommunityAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ADMIN - Quản lý / kiểm duyệt CỘNG ĐỒNG (mạng xã hội du lịch). Cần JWT role ADMIN/SUPER_ADMIN
 * (đã được chặn ở SecurityApiConfig: /api/admin/** hasAnyRole ADMIN,SUPER_ADMIN).
 *
 * Nhóm endpoint: dashboard, báo cáo (hàng đợi kiểm duyệt), bài viết, bình luận, thành viên.
 * Mỗi thao tác thay đổi trạng thái đều ghi AuditEvent (đồng nhất AdminReviewApiController).
 */
@Tag(name = "Admin - Quản lý cộng đồng", description = "Kiểm duyệt bài/bình luận/báo cáo + quản lý thành viên. JWT ADMIN/SUPER_ADMIN.")
@RestController
@RequestMapping("/api/admin/v1/community")
public class AdminCommunityApiController {

    private final CommunityAdminService svc;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public AdminCommunityApiController(CommunityAdminService svc, UserRepository userRepository,
                                       ApplicationEventPublisher events) {
        this.svc = svc;
        this.userRepository = userRepository;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    // ===================== DASHBOARD =====================

    @Operation(summary = "Thống kê cộng đồng (bài/bình luận/báo cáo theo trạng thái, thành viên, top hashtag)")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(svc.stats());
    }

    // ===================== REPORTS (hàng đợi kiểm duyệt) =====================

    @Operation(summary = "Danh sách báo cáo nội dung (lọc ?status=OPEN/REVIEWED/ACTIONED/DISMISSED)")
    @GetMapping("/reports")
    public ApiResponse<PagedResponse<AdminSocialReportDto>> reports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContentReport> pg = svc.listReports(status, page, size);
        Set<Long> ids = pg.getContent().stream().map(ContentReport::getReporterUserId).collect(Collectors.toSet());
        Map<Long, String> names = svc.userNames(ids);
        Page<AdminSocialReportDto> dto = pg.map(r -> AdminSocialReportDto.from(r, names.get(r.getReporterUserId())));
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    @Operation(summary = "Xử lý báo cáo: ẨN nội dung bị tố + đóng báo cáo (ACTIONED)")
    @PostMapping("/reports/{id}/hide")
    public ApiResponse<Void> reportHide(@PathVariable Long id, Authentication auth) {
        svc.reportHide(id, actorId(auth));
        events.publishEvent(new AuditEvent(actorId(auth), "REPORT_HIDE", "SOCIAL_REPORT", id, "Ẩn nội dung bị báo cáo"));
        return ApiResponse.ok(null, "Đã ẩn nội dung");
    }

    @Operation(summary = "Xử lý báo cáo: GỠ hẳn nội dung bị tố + đóng báo cáo (ACTIONED)")
    @PostMapping("/reports/{id}/remove")
    public ApiResponse<Void> reportRemove(@PathVariable Long id, Authentication auth) {
        svc.reportRemove(id, actorId(auth));
        events.publishEvent(new AuditEvent(actorId(auth), "REPORT_REMOVE", "SOCIAL_REPORT", id, "Gỡ nội dung bị báo cáo"));
        return ApiResponse.ok(null, "Đã gỡ nội dung");
    }

    @Operation(summary = "Xử lý báo cáo: BỎ QUA (giữ nội dung) + đóng báo cáo (DISMISSED)")
    @PostMapping("/reports/{id}/dismiss")
    public ApiResponse<Void> reportDismiss(@PathVariable Long id, Authentication auth) {
        svc.reportDismiss(id, actorId(auth));
        events.publishEvent(new AuditEvent(actorId(auth), "REPORT_DISMISS", "SOCIAL_REPORT", id, "Bỏ qua báo cáo"));
        return ApiResponse.ok(null, "Đã bỏ qua báo cáo");
    }

    // ===================== POSTS =====================

    @Operation(summary = "Danh sách bài viết (lọc ?status=&authorId=&q=, gồm cả HIDDEN/REMOVED)")
    @GetMapping("/posts")
    public ApiResponse<PagedResponse<AdminSocialPostDto>> posts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Post> pg = svc.listPosts(status, authorId, q, page, size);
        Set<Long> ids = pg.getContent().stream().map(Post::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, String> names = svc.userNames(ids);
        Page<AdminSocialPostDto> dto = pg.map(p -> AdminSocialPostDto.from(p, names.get(p.getAuthorUserId())));
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    @Operation(summary = "Ẩn tạm 1 bài (HIDDEN)")
    @PostMapping("/posts/{id}/hide")
    public ApiResponse<AdminSocialPostDto> hidePost(@PathVariable Long id, Authentication auth) {
        AdminSocialPostDto dto = enrich(svc.hidePost(id));
        events.publishEvent(new AuditEvent(actorId(auth), "HIDE_POST", "SOCIAL_POST", id, "Ẩn bài viết"));
        return ApiResponse.ok(dto, "Đã ẩn bài");
    }

    @Operation(summary = "Hiện lại 1 bài (PUBLISHED) — dùng để bỏ ẩn bài bị auto-hide")
    @PostMapping("/posts/{id}/unhide")
    public ApiResponse<AdminSocialPostDto> unhidePost(@PathVariable Long id, Authentication auth) {
        AdminSocialPostDto dto = enrich(svc.unhidePost(id));
        events.publishEvent(new AuditEvent(actorId(auth), "UNHIDE_POST", "SOCIAL_POST", id, "Hiện lại bài viết"));
        return ApiResponse.ok(dto, "Đã hiện bài");
    }

    @Operation(summary = "Khôi phục 1 bài đã gỡ (REMOVED -> PUBLISHED)")
    @PostMapping("/posts/{id}/restore")
    public ApiResponse<Void> restorePost(@PathVariable Long id, Authentication auth) {
        svc.restorePost(id);
        events.publishEvent(new AuditEvent(actorId(auth), "RESTORE_POST", "SOCIAL_POST", id, "Khôi phục bài viết"));
        return ApiResponse.ok(null, "Đã khôi phục bài");
    }

    @Operation(summary = "Gỡ hẳn 1 bài (soft delete, REMOVED)")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> removePost(@PathVariable Long id, Authentication auth) {
        svc.removePost(id, actorId(auth));
        events.publishEvent(new AuditEvent(actorId(auth), "REMOVE_POST", "SOCIAL_POST", id, "Gỡ bài viết"));
        return ApiResponse.ok(null, "Đã gỡ bài");
    }

    // ===================== COMMENTS =====================

    @Operation(summary = "Danh sách bình luận (lọc ?status=&authorId=&postId=, gồm cả HIDDEN/REMOVED)")
    @GetMapping("/comments")
    public ApiResponse<PagedResponse<AdminSocialCommentDto>> comments(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Comment> pg = svc.listComments(status, authorId, postId, page, size);
        Set<Long> ids = pg.getContent().stream().map(Comment::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, String> names = svc.userNames(ids);
        Page<AdminSocialCommentDto> dto = pg.map(c -> AdminSocialCommentDto.from(c, names.get(c.getAuthorUserId())));
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    @Operation(summary = "Ẩn tạm 1 bình luận (HIDDEN)")
    @PostMapping("/comments/{id}/hide")
    public ApiResponse<Void> hideComment(@PathVariable Long id, Authentication auth) {
        svc.hideComment(id);
        events.publishEvent(new AuditEvent(actorId(auth), "HIDE_COMMENT", "SOCIAL_COMMENT", id, "Ẩn bình luận"));
        return ApiResponse.ok(null, "Đã ẩn bình luận");
    }

    @Operation(summary = "Hiện lại 1 bình luận (PUBLISHED)")
    @PostMapping("/comments/{id}/unhide")
    public ApiResponse<Void> unhideComment(@PathVariable Long id, Authentication auth) {
        svc.unhideComment(id);
        events.publishEvent(new AuditEvent(actorId(auth), "UNHIDE_COMMENT", "SOCIAL_COMMENT", id, "Hiện lại bình luận"));
        return ApiResponse.ok(null, "Đã hiện bình luận");
    }

    @Operation(summary = "Khôi phục 1 bình luận đã gỡ (REMOVED -> PUBLISHED)")
    @PostMapping("/comments/{id}/restore")
    public ApiResponse<Void> restoreComment(@PathVariable Long id, Authentication auth) {
        svc.restoreComment(id);
        events.publishEvent(new AuditEvent(actorId(auth), "RESTORE_COMMENT", "SOCIAL_COMMENT", id, "Khôi phục bình luận"));
        return ApiResponse.ok(null, "Đã khôi phục bình luận");
    }

    @Operation(summary = "Gỡ hẳn 1 bình luận (soft delete, REMOVED)")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> removeComment(@PathVariable Long id, Authentication auth) {
        svc.removeComment(id, actorId(auth));
        events.publishEvent(new AuditEvent(actorId(auth), "REMOVE_COMMENT", "SOCIAL_COMMENT", id, "Gỡ bình luận"));
        return ApiResponse.ok(null, "Đã gỡ bình luận");
    }

    // ===================== MEMBERS =====================

    @Operation(summary = "Danh sách thành viên cộng đồng (tìm ?q= theo handle/tên)")
    @GetMapping("/members")
    public ApiResponse<PagedResponse<AdminSocialMemberDto>> members(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SocialProfile> pg = svc.listMembers(q, page, size);
        Set<Long> ids = pg.getContent().stream().map(SocialProfile::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = svc.usersById(ids);
        Page<AdminSocialMemberDto> dto = pg.map(p -> AdminSocialMemberDto.from(p, users.get(p.getUserId())));
        return ApiResponse.ok(PagedResponse.of(dto));
    }

    @Operation(summary = "Khoá tài khoản 1 thành viên vi phạm (UserStatus.LOCKED — chặn đăng nhập)")
    @PostMapping("/members/{userId}/lock")
    public ApiResponse<Void> lockMember(@PathVariable Long userId, Authentication auth) {
        return setUserStatus(userId, UserStatus.LOCKED, auth, "LOCK_MEMBER", "Đã khoá tài khoản");
    }

    @Operation(summary = "Mở khoá tài khoản 1 thành viên (UserStatus.ACTIVE)")
    @PostMapping("/members/{userId}/unlock")
    public ApiResponse<Void> unlockMember(@PathVariable Long userId, Authentication auth) {
        return setUserStatus(userId, UserStatus.ACTIVE, auth, "UNLOCK_MEMBER", "Đã mở khoá tài khoản");
    }

    // ===================== helpers =====================

    /** Đổi trạng thái tài khoản (khoá/mở) với chốt an toàn: không tự nhắm mình; đổi user có quyền cần SUPER_ADMIN. */
    private ApiResponse<Void> setUserStatus(Long userId, UserStatus status, Authentication auth,
                                            String action, String okMessage) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        Long actor = actorId(auth);
        if (actor != null && actor.equals(u.getId())) {
            throw new BusinessException("SELF_TARGET", "Không thể tự khoá/mở khoá chính mình", HttpStatus.BAD_REQUEST);
        }
        Role targetRole = u.getRole();
        if (targetRole == Role.ADMIN || targetRole == Role.SUPER_ADMIN || targetRole == Role.VENDOR) {
            RoleUtils.requireSuperAdmin(auth);
        }
        u.setStatus(status);
        userRepository.save(u);
        events.publishEvent(new AuditEvent(actor, action, "USER", userId, "status=" + status));
        return ApiResponse.ok(null, okMessage);
    }

    private AdminSocialPostDto enrich(Post p) {
        Map<Long, String> n = svc.userNames(Set.of(p.getAuthorUserId()));
        return AdminSocialPostDto.from(p, n.get(p.getAuthorUserId()));
    }
}
