package com.dididi.booking.corporate.service;

import com.dididi.booking.approval.service.ApprovalService;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Thanh toan don bang NGAN SACH CONG TY (B2B). Vuot nguong -> cho phe duyet; het han muc -> chan. */
@Service
public class CorporateBookingService {

    private final BookingService bookingService;
    private final CompanyService companyService;
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final ApprovalService approvalService;

    public CorporateBookingService(BookingService bookingService, CompanyService companyService,
                                   PaymentService paymentService, UserRepository userRepository,
                                   ApprovalService approvalService) {
        this.bookingService = bookingService;
        this.companyService = companyService;
        this.paymentService = paymentService;
        this.userRepository = userRepository;
        this.approvalService = approvalService;
    }

    @Transactional
    public CorporatePaymentOutcome payWithCompanyBudget(String code, Long userId) {
        Booking b = bookingService.getForUser(code, userId); // dam bao don thuoc user
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("CANNOT_PAY",
                    "Đơn không ở trạng thái chờ thanh toán (hiện tại: " + b.getStatus() + ")", HttpStatus.CONFLICT);
        }
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        Long companyId = u.getCompanyId();
        if (companyId == null) {
            throw new BusinessException("NO_COMPANY", "Tài khoản của bạn chưa thuộc công ty nào", HttpStatus.CONFLICT);
        }
        Company company = companyService.get(companyId);
        if (!company.isActive()) {
            throw new BusinessException("COMPANY_INACTIVE", "Công ty đang bị khoá", HttpStatus.CONFLICT);
        }

        // Vuot nguong duyet -> tao yeu cau phe duyet, CHUA tru ngan sach / xac nhan.
        BigDecimal threshold = company.getApprovalThreshold();
        if (threshold != null && threshold.signum() > 0 && b.getAmount().compareTo(threshold) > 0) {
            approvalService.requestApproval(b, companyId, userId);
            return CorporatePaymentOutcome.PENDING_APPROVAL;
        }

        // Duoi nguong -> tru han muc (vuot -> BUDGET_EXCEEDED, rollback) + xac nhan ngay.
        companyService.charge(companyId, b.getAmount());
        paymentService.payByCompany(b);
        b.setCompanyId(companyId);
        bookingService.markConfirmed(b);
        return CorporatePaymentOutcome.CONFIRMED;
    }
}
