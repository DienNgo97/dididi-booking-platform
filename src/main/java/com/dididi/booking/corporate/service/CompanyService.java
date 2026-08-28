package com.dididi.booking.corporate.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.api.dto.CompanyUpsertRequest;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.domain.entity.CompanyBudgetTxn;
import com.dididi.booking.corporate.repository.CompanyBudgetTxnRepository;
import com.dididi.booking.corporate.repository.CompanyRepository;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CompanyBudgetTxnRepository budgetTxnRepository;

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository,
                          BookingRepository bookingRepository, CompanyBudgetTxnRepository budgetTxnRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.budgetTxnRepository = budgetTxnRepository;
    }

    /** Sổ cái biến động ngân sách của 1 công ty (mới nhất trước) — cho trang đối soát admin. */
    public List<CompanyBudgetTxn> budgetLedger(Long companyId) {
        return budgetTxnRepository.findByCompanyIdOrderByIdDesc(companyId);
    }

    public List<Company> list() { return companyRepository.findAllByOrderByName(); }

    public List<Company> listActive() { return companyRepository.findByActiveTrueOrderByName(); }

    public Company get(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy công ty", HttpStatus.NOT_FOUND));
    }

    public Company getOrNull(Long id) {
        return id == null ? null : companyRepository.findById(id).orElse(null);
    }

    @Transactional
    public Company create(CompanyUpsertRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException("INVALID", "Thiếu mã công ty", HttpStatus.BAD_REQUEST);
        }
        if (companyRepository.existsByCode(req.code())) {
            throw new BusinessException("CODE_EXISTS", "Mã công ty đã tồn tại", HttpStatus.CONFLICT);
        }
        Company c = new Company();
        c.setName(req.name());
        c.setCode(req.code());
        c.setBudgetTotal(req.budgetTotal() == null ? BigDecimal.ZERO : req.budgetTotal());
        c.setBudgetUsed(BigDecimal.ZERO);
        c.setContactEmail(req.contactEmail());
        c.setTaxCode(req.taxCode());
        c.setAddress(req.address());
        c.setApprovalThreshold(req.approvalThreshold());
        c.setActive(req.active() == null || req.active());
        return companyRepository.save(c);
    }

    @Transactional
    public Company update(Long id, CompanyUpsertRequest req) {
        Company c = get(id);
        if (req.code() != null && !req.code().isBlank() && !req.code().equals(c.getCode())) {
            if (companyRepository.existsByCode(req.code())) {
                throw new BusinessException("CODE_EXISTS", "Mã công ty đã tồn tại", HttpStatus.CONFLICT);
            }
            c.setCode(req.code());
        }
        if (req.name() != null) c.setName(req.name());
        if (req.budgetTotal() != null) c.setBudgetTotal(req.budgetTotal());
        if (req.contactEmail() != null) c.setContactEmail(req.contactEmail());
        if (req.taxCode() != null) c.setTaxCode(req.taxCode());
        if (req.address() != null) c.setAddress(req.address());
        if (req.approvalThreshold() != null) c.setApprovalThreshold(req.approvalThreshold());
        if (req.active() != null) c.setActive(req.active());
        return companyRepository.save(c);
    }

    @Transactional
    public Company topUp(Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("INVALID", "Số tiền nạp phải lớn hơn 0", HttpStatus.BAD_REQUEST);
        }
        Company c = lockCompany(id);     // nạp tiền cũng là cộng dồn trên số dư -> phải khoá dòng
        c.setBudgetTotal(c.getBudgetTotal().add(amount));
        return companyRepository.save(c);
    }

    /** Đọc công ty kèm khoá bi quan; không thấy thì báo lỗi giống {@link #get}. */
    private Company lockCompany(Long id) {
        return companyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy công ty", HttpStatus.NOT_FOUND));
    }

    public List<User> listEmployees(Long companyId) {
        get(companyId);
        return userRepository.findByCompanyIdOrderByEmail(companyId);
    }

    @Transactional
    public void assignEmployee(Long companyId, Long userId) {
        get(companyId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        u.setCompanyId(companyId);
        userRepository.save(u);
    }

    @Transactional
    public void unassignEmployee(Long companyId, Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        if (companyId.equals(u.getCompanyId())) {
            u.setCompanyId(null);
            userRepository.save(u);
        }
    }

    public List<Booking> companyBookings(Long companyId) {
        get(companyId);
        return bookingRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    /** Cong ty cua 1 user (chi tra ve khi dang active). */
    public Optional<Company> forUser(Long userId) {
        return userRepository.findById(userId)
                .map(User::getCompanyId).filter(Objects::nonNull)
                .flatMap(companyRepository::findById)
                .filter(Company::isActive);
    }

    /**
     * Tru han muc cong ty; neu khong du -> chan (BUDGET_EXCEEDED).
     * P1-7: đọc dòng công ty bằng khoá bi quan để hai đơn cùng lúc không cùng thấy "còn đủ".
     */
    @Transactional
    public void charge(Long companyId, BigDecimal amount, Long bookingId) {
        Company c = lockCompany(companyId);
        if (!c.isActive()) {
            throw new BusinessException("COMPANY_INACTIVE", "Công ty đang bị khoá", HttpStatus.CONFLICT);
        }
        BigDecimal remaining = c.remaining();
        if (remaining.compareTo(amount) < 0) {
            throw new BusinessException("BUDGET_EXCEEDED",
                    "Hạn mức công ty đã hết (còn " + remaining.toBigInteger() + " VND, cần "
                            + amount.toBigInteger() + " VND). Vui lòng thông báo phòng Tài chính - Kế toán nạp thêm tiền.",
                    HttpStatus.CONFLICT);
        }
        c.setBudgetUsed(c.getBudgetUsed().add(amount));
        companyRepository.save(c);
        budgetTxnRepository.save(new CompanyBudgetTxn(companyId, bookingId, amount, "CHARGE", "Trừ hạn mức cho đơn"));
    }

    /**
     * Hoan lai han muc cong ty khi REFUND don da tru ngan sach (dao nguoc {@link #charge}).
     * Goi tu RefundService. Idempotent o tang goi (moi don chi refund 1 lan, Payment->REFUNDED chan lap lai).
     * Khong de budgetUsed am.
     */
    @Transactional
    public void release(Long companyId, BigDecimal amount, Long bookingId) {
        if (companyId == null || amount == null || amount.signum() <= 0) return;
        // Khoá dòng như charge: hoàn hạn mức cũng là đọc-rồi-ghi trên cùng con số budgetUsed.
        Company c = companyRepository.findByIdForUpdate(companyId).orElse(null);
        if (c == null) return;
        BigDecimal used = c.getBudgetUsed() == null ? BigDecimal.ZERO : c.getBudgetUsed();
        BigDecimal newUsed = used.subtract(amount);
        c.setBudgetUsed(newUsed.signum() < 0 ? BigDecimal.ZERO : newUsed);
        companyRepository.save(c);
        budgetTxnRepository.save(new CompanyBudgetTxn(companyId, bookingId, amount, "RELEASE", "Hoàn hạn mức do refund"));
    }
}
