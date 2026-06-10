package com.dididi.booking.invite.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.invite.api.dto.CompanyInviteDto;
import com.dididi.booking.invite.domain.CompanyInvite;
import com.dididi.booking.invite.domain.InviteStatus;
import com.dididi.booking.invite.repository.CompanyInviteRepository;
import com.dididi.booking.notification.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Moi nguoi dat (booker) tham gia cong ty B2B qua link/email. */
@Service
public class CompanyInviteService {

    private final CompanyInviteRepository repository;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public CompanyInviteService(CompanyInviteRepository repository, CompanyService companyService,
                                UserRepository userRepository, EmailService emailService) {
        this.repository = repository;
        this.companyService = companyService;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional
    public CompanyInviteDto create(Long companyId, String email, Long invitedByUserId) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("INVALID", "Thiếu email người được mời", HttpStatus.BAD_REQUEST);
        }
        Company c = companyService.get(companyId); // nem neu khong ton tai
        CompanyInvite i = new CompanyInvite();
        i.setCompanyId(companyId);
        i.setEmail(email.trim());
        i.setToken(UUID.randomUUID().toString().replace("-", ""));
        i.setStatus(InviteStatus.PENDING);
        i.setInvitedByUserId(invitedByUserId);
        i.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        repository.save(i);
        emailService.sendCompanyInvite(i.getEmail(), c.getName(), baseUrl + "/company-invite/" + i.getToken());
        return CompanyInviteDto.from(i, baseUrl);
    }

    public List<CompanyInviteDto> list(Long companyId) {
        return repository.findByCompanyIdOrderByIdDesc(companyId).stream()
                .map(i -> CompanyInviteDto.from(i, baseUrl)).toList();
    }

    @Transactional
    public void revoke(Long companyId, Long inviteId) {
        CompanyInvite i = repository.findById(inviteId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy lời mời", HttpStatus.NOT_FOUND));
        if (!i.getCompanyId().equals(companyId)) {
            throw new BusinessException("MISMATCH", "Lời mời không thuộc công ty này", HttpStatus.BAD_REQUEST);
        }
        i.setStatus(InviteStatus.REVOKED);
        repository.save(i);
    }

    public CompanyInvite findByToken(String token) {
        return repository.findByToken(token).orElse(null);
    }

    /** Nguoi dung dang nhap chap nhan loi moi -> gan companyId. Tra ve ten cong ty. */
    @Transactional
    public String accept(String token, User user) {
        CompanyInvite i = repository.findByToken(token)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Lời mời không tồn tại", HttpStatus.NOT_FOUND));
        if (i.getStatus() != InviteStatus.PENDING) {
            throw new BusinessException("INVALID_INVITE", "Lời mời đã được dùng hoặc đã thu hồi", HttpStatus.CONFLICT);
        }
        if (i.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("EXPIRED", "Lời mời đã hết hạn", HttpStatus.CONFLICT);
        }
        if (!i.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException("EMAIL_MISMATCH",
                    "Lời mời dành cho email khác (" + i.getEmail() + "). Hãy đăng nhập bằng đúng email được mời.",
                    HttpStatus.FORBIDDEN);
        }
        Company c = companyService.get(i.getCompanyId());
        user.setCompanyId(i.getCompanyId());
        userRepository.save(user);
        i.setStatus(InviteStatus.ACCEPTED);
        repository.save(i);
        return c.getName();
    }
}
