package com.dididi.booking.approval.service;

import com.dididi.booking.approval.api.dto.ApprovalRequestDto;
import com.dididi.booking.approval.domain.ApprovalRequest;
import com.dididi.booking.approval.domain.ApprovalStatus;
import com.dididi.booking.approval.repository.ApprovalRequestRepository;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.service.CompanyService;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Phe duyet don tra bang ngan sach cong ty (vuot nguong). Phe duyet -> tru ngan sach + xac nhan. */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final CompanyService companyService;
    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public ApprovalService(ApprovalRequestRepository repository, BookingRepository bookingRepository,
                           BookingService bookingService, CompanyService companyService,
                           PaymentService paymentService, UserRepository userRepository) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.companyService = companyService;
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    /** Tao yeu cau phe duyet (PENDING) cho 1 don - goi tu CorporateBookingService khi vuot nguong. */
    @Transactional
    public ApprovalRequest requestApproval(Booking b, Long companyId, Long userId) {
        ApprovalRequest existing = repository.findFirstByBookingIdOrderByIdDesc(b.getId()).orElse(null);
        if (existing != null && existing.getStatus() == ApprovalStatus.PENDING) {
            return existing; // tranh tao trung khi bam lai
        }
        ApprovalRequest r = new ApprovalRequest();
        r.setBookingId(b.getId());
        r.setCompanyId(companyId);
        r.setRequestedByUserId(userId);
        r.setAmount(b.getAmount());
        r.setStatus(ApprovalStatus.PENDING);
        return repository.save(r);
    }

    public ApprovalRequest latestForBooking(Long bookingId) {
        return repository.findFirstByBookingIdOrderByIdDesc(bookingId).orElse(null);
    }

    public boolean isPendingApproval(Long bookingId) {
        ApprovalRequest r = latestForBooking(bookingId);
        return r != null && r.getStatus() == ApprovalStatus.PENDING;
    }

    public List<ApprovalRequestDto> listPending(Long companyId) {
        List<ApprovalRequest> list = (companyId == null)
                ? repository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING)
                : repository.findByCompanyIdAndStatusOrderByIdDesc(companyId, ApprovalStatus.PENDING);
        List<ApprovalRequestDto> out = new ArrayList<>();
        for (ApprovalRequest r : list) out.add(toDto(r));
        return out;
    }

    @Transactional
    public ApprovalRequestDto approve(Long id, Long adminUserId) {
        ApprovalRequest r = getPending(id);
        Booking b = bookingRepository.findById(r.getBookingId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("CANNOT_APPROVE",
                    "Đơn không còn ở trạng thái chờ thanh toán (hiện tại: " + b.getStatus() + ")", HttpStatus.CONFLICT);
        }
        // Tru ngan sach (vuot -> BUDGET_EXCEEDED, rollback) roi xac nhan.
        companyService.charge(r.getCompanyId(), b.getAmount());
        paymentService.payByCompany(b);
        b.setCompanyId(r.getCompanyId());
        bookingService.markConfirmed(b);
        r.setStatus(ApprovalStatus.APPROVED);
        r.setDecidedByUserId(adminUserId);
        repository.save(r);
        return toDto(r);
    }

    @Transactional
    public ApprovalRequestDto reject(Long id, Long adminUserId, String note) {
        ApprovalRequest r = getPending(id);
        r.setStatus(ApprovalStatus.REJECTED);
        r.setDecidedByUserId(adminUserId);
        r.setDecisionNote(note);
        repository.save(r);
        return toDto(r);
    }

    private ApprovalRequest getPending(Long id) {
        ApprovalRequest r = repository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy yêu cầu", HttpStatus.NOT_FOUND));
        if (r.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("ALREADY_DECIDED", "Yêu cầu đã được xử lý", HttpStatus.CONFLICT);
        }
        return r;
    }

    private ApprovalRequestDto toDto(ApprovalRequest r) {
        Booking b = bookingRepository.findById(r.getBookingId()).orElse(null);
        Company co = companyService.getOrNull(r.getCompanyId());
        User u = userRepository.findById(r.getRequestedByUserId()).orElse(null);
        return new ApprovalRequestDto(r.getId(),
                b != null ? b.getPublicCode() : null, b != null ? b.getTitle() : null,
                r.getCompanyId(), co != null ? co.getName() : null,
                u != null ? u.getEmail() : null, r.getAmount(), r.getStatus().name(),
                r.getDecisionNote(), r.getCreatedAt());
    }
}
