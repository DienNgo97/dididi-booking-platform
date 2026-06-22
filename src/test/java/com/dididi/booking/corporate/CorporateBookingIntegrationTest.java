package com.dididi.booking.corporate;

import com.dididi.booking.approval.domain.ApprovalRequest;
import com.dididi.booking.approval.domain.ApprovalStatus;
import com.dididi.booking.approval.repository.ApprovalRequestRepository;
import com.dididi.booking.approval.service.ApprovalService;
import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.corporate.domain.entity.Company;
import com.dididi.booking.corporate.repository.CompanyRepository;
import com.dididi.booking.corporate.service.CorporateBookingService;
import com.dididi.booking.corporate.service.CorporatePaymentOutcome;
import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import com.dididi.booking.identity.domain.enums.UserStatus;
import com.dididi.booking.identity.repository.UserRepository;
import com.dididi.booking.invite.domain.CompanyInvite;
import com.dididi.booking.invite.domain.InviteStatus;
import com.dididi.booking.invite.repository.CompanyInviteRepository;
import com.dididi.booking.invite.service.CompanyInviteService;
import com.dididi.booking.payment.domain.entity.Payment;
import com.dididi.booking.payment.domain.enums.PaymentStatus;
import com.dididi.booking.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test cho luong B2B (Phan 3 trong KE_HOACH_TEST.md):
 *   3.1  Nhan vien dat - cong ty tra (duoi nguong)  -> CONFIRMED + tru ngan sach
 *   3.2  Vuot nguong duyet                            -> tao ApprovalRequest PENDING, KHONG tru tien
 *   3.2/5.10  Admin duyet yeu cau                     -> tru ngan sach + xac nhan
 *   3.3  Het ngan sach                                -> BUDGET_EXCEEDED, rollback
 *   3.4  Loi moi cong ty (create -> accept)           -> gan companyId; sai email -> EMAIL_MISMATCH
 *   + guard: user chua thuoc cong ty                  -> NO_COMPANY
 *
 * Test goi thang vao service (CorporateBookingService / ApprovalService / CompanyInviteService),
 * khong qua web/session, nen khong can bat flight-provider / hotel-pms / Angular.
 *
 * Chay:  ./mvnw test -Dtest=CorporateBookingIntegrationTest      (can Docker dang chay - Testcontainers)
 *
 * Profile "test" -> cac seeder (@Profile("dev")) khong chay, va application-test.yml tat gui email.
 * Moi entity dung ma/email duy nhat (uid) nen khong dung do du lieu test khac.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(CorporateTestContainers.class)
@DisplayName("Phan 3 - Doanh nghiep B2B (cong ty thanh toan / duyet chi tieu / loi moi)")
class CorporateBookingIntegrationTest {

    @Autowired CorporateBookingService corporateBookingService;
    @Autowired ApprovalService approvalService;
    @Autowired CompanyInviteService companyInviteService;

    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ApprovalRequestRepository approvalRequestRepository;
    @Autowired CompanyInviteRepository companyInviteRepository;

    private static final AtomicLong SEQ = new AtomicLong();

    // ---------------------------------------------------------------- 3.1

    @Test
    @DisplayName("3.1 Don duoi nguong -> CONFIRMED va tru dung ngan sach cong ty")
    void underThreshold_confirmsAndChargesBudget() {
        Company co = newCompany("20000000", "5000000");          // ngan 20tr, nguong 5tr
        User employee = newUser(Role.CUSTOMER, co.getId());
        Booking b = newPendingBooking(employee.getId(), "3000000"); // 3tr <= 5tr -> di thang

        CorporatePaymentOutcome outcome =
                corporateBookingService.payWithCompanyBudget(b.getPublicCode(), employee.getId());

        assertThat(outcome).isEqualTo(CorporatePaymentOutcome.CONFIRMED);

        Company after = companyRepository.findById(co.getId()).orElseThrow();
        assertThat(after.getBudgetUsed()).isEqualByComparingTo("3000000");
        assertThat(after.remaining()).isEqualByComparingTo("17000000");

        Booking confirmed = bookingRepository.findByPublicCode(b.getPublicCode()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getCompanyId()).isEqualTo(co.getId());

        Payment p = paymentRepository.findByBookingId(confirmed.getId()).orElseThrow();
        assertThat(p.getMethod()).isEqualTo("COMPANY_BUDGET");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getAmount()).isEqualByComparingTo("3000000");

        // Don duoi nguong khong tao yeu cau duyet.
        assertThat(approvalRequestRepository.findFirstByBookingIdOrderByIdDesc(confirmed.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- 3.2

    @Test
    @DisplayName("3.2 Don vuot nguong -> tao ApprovalRequest PENDING, KHONG tru tien / KHONG xac nhan")
    void overThreshold_createsPendingApprovalWithoutCharging() {
        Company co = newCompany("50000000", "5000000");
        User employee = newUser(Role.CUSTOMER, co.getId());
        Booking b = newPendingBooking(employee.getId(), "6000000"); // 6tr > 5tr -> cho duyet

        CorporatePaymentOutcome outcome =
                corporateBookingService.payWithCompanyBudget(b.getPublicCode(), employee.getId());

        assertThat(outcome).isEqualTo(CorporatePaymentOutcome.PENDING_APPROVAL);

        // Chua tru ngan sach, don van cho thanh toan.
        Company after = companyRepository.findById(co.getId()).orElseThrow();
        assertThat(after.getBudgetUsed()).isEqualByComparingTo("0");
        Booking still = bookingRepository.findByPublicCode(b.getPublicCode()).orElseThrow();
        assertThat(still.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        // Co dung 1 yeu cau duyet PENDING dung so tien.
        ApprovalRequest req = approvalRequestRepository.findFirstByBookingIdOrderByIdDesc(b.getId()).orElseThrow();
        assertThat(req.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(req.getAmount()).isEqualByComparingTo("6000000");
        assertThat(req.getCompanyId()).isEqualTo(co.getId());

        // Chua co thanh toan.
        assertThat(paymentRepository.findByBookingId(b.getId())).isEmpty();
    }

    @Test
    @DisplayName("3.2/5.10 Admin duyet yeu cau -> tru ngan sach + don CONFIRMED + request APPROVED")
    void approvePendingRequest_confirmsAndChargesBudget() {
        Company co = newCompany("50000000", "5000000");
        User employee = newUser(Role.CUSTOMER, co.getId());
        User admin = newUser(Role.ADMIN, null);
        Booking b = newPendingBooking(employee.getId(), "6000000");

        // Tao yeu cau duyet (vuot nguong)
        corporateBookingService.payWithCompanyBudget(b.getPublicCode(), employee.getId());
        ApprovalRequest req = approvalRequestRepository.findFirstByBookingIdOrderByIdDesc(b.getId()).orElseThrow();

        // Admin duyet
        approvalService.approve(req.getId(), admin.getId());

        Company after = companyRepository.findById(co.getId()).orElseThrow();
        assertThat(after.getBudgetUsed()).isEqualByComparingTo("6000000");

        Booking confirmed = bookingRepository.findByPublicCode(b.getPublicCode()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getCompanyId()).isEqualTo(co.getId());

        ApprovalRequest decided = approvalRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    // ---------------------------------------------------------------- 3.3

    @Test
    @DisplayName("3.3 Don duoi nguong nhung vuot ngan sach con lai -> BUDGET_EXCEEDED + rollback")
    void belowThresholdButOverRemaining_throwsBudgetExceeded() {
        Company co = newCompany("1000000", "5000000");           // chi con 1tr, nguong 5tr
        User employee = newUser(Role.CUSTOMER, co.getId());
        Booking b = newPendingBooking(employee.getId(), "2000000"); // 2tr <= 5tr (di thang) nhung > 1tr con lai

        assertThatThrownBy(() ->
                corporateBookingService.payWithCompanyBudget(b.getPublicCode(), employee.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("BUDGET_EXCEEDED"));

        // Rollback: ngan sach khong doi, don van PENDING_PAYMENT, khong tao thanh toan.
        Company after = companyRepository.findById(co.getId()).orElseThrow();
        assertThat(after.getBudgetUsed()).isEqualByComparingTo("0");
        Booking still = bookingRepository.findByPublicCode(b.getPublicCode()).orElseThrow();
        assertThat(still.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findByBookingId(b.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- guard

    @Test
    @DisplayName("Guard: user chua thuoc cong ty nao -> NO_COMPANY")
    void userWithoutCompany_throwsNoCompany() {
        User lone = newUser(Role.CUSTOMER, null);                // companyId = null
        Booking b = newPendingBooking(lone.getId(), "1000000");

        assertThatThrownBy(() ->
                corporateBookingService.payWithCompanyBudget(b.getPublicCode(), lone.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NO_COMPANY"));
    }

    // ---------------------------------------------------------------- 3.4

    @Test
    @DisplayName("3.4 Tao loi moi roi chap nhan -> user duoc gan companyId, invite -> ACCEPTED")
    void inviteCreatedThenAccepted_bindsUserToCompany() {
        Company co = newCompany("50000000", "5000000");
        User admin = newUser(Role.ADMIN, co.getId());
        String inviteeEmail = "invitee-" + uid() + "@dididi.local";

        // 5.9: admin tao loi moi
        companyInviteService.create(co.getId(), inviteeEmail, admin.getId());
        CompanyInvite invite = companyInviteRepository.findByCompanyIdOrderByIdDesc(co.getId()).get(0);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.PENDING);

        // Nguoi duoc moi (dang nhap bang DUNG email duoc moi), chua thuoc cong ty nao
        User invitee = newUserWithEmail(inviteeEmail, null);
        assertThat(invitee.getCompanyId()).isNull();

        String companyName = companyInviteService.accept(invite.getToken(), invitee);
        assertThat(companyName).isEqualTo(co.getName());

        User bound = userRepository.findById(invitee.getId()).orElseThrow();
        assertThat(bound.getCompanyId()).isEqualTo(co.getId());

        CompanyInvite used = companyInviteRepository.findByToken(invite.getToken()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    @DisplayName("3.4 Chap nhan loi moi bang email khac -> EMAIL_MISMATCH")
    void inviteAcceptedWithWrongEmail_throwsEmailMismatch() {
        Company co = newCompany("50000000", "5000000");
        User admin = newUser(Role.ADMIN, co.getId());
        String inviteeEmail = "invitee-" + uid() + "@dididi.local";

        companyInviteService.create(co.getId(), inviteeEmail, admin.getId());
        CompanyInvite invite = companyInviteRepository.findByCompanyIdOrderByIdDesc(co.getId()).get(0);

        // Dang nhap bang email khac voi email duoc moi
        User wrongUser = newUserWithEmail("someone-else-" + uid() + "@dididi.local", null);

        assertThatThrownBy(() -> companyInviteService.accept(invite.getToken(), wrongUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("EMAIL_MISMATCH"));

        // Khong gan companyId, invite van PENDING
        assertThat(userRepository.findById(wrongUser.getId()).orElseThrow().getCompanyId()).isNull();
        assertThat(companyInviteRepository.findByToken(invite.getToken()).orElseThrow().getStatus())
                .isEqualTo(InviteStatus.PENDING);
    }

    // ---------------------------------------------------------------- helpers

    private String uid() {
        return Long.toString(SEQ.incrementAndGet());
    }

    private Company newCompany(String budgetTotal, String approvalThreshold) {
        String s = uid();
        Company c = new Company();
        c.setName("Test Co " + s);
        c.setCode("TCO" + s);
        c.setBudgetTotal(new BigDecimal(budgetTotal));
        c.setBudgetUsed(BigDecimal.ZERO);
        c.setApprovalThreshold(approvalThreshold == null ? null : new BigDecimal(approvalThreshold));
        c.setActive(true);
        return companyRepository.save(c);
    }

    private User newUser(Role role, Long companyId) {
        return newUserWithEmail("test-" + uid() + "@dididi.local", companyId, role);
    }

    private User newUserWithEmail(String email, Long companyId) {
        return newUserWithEmail(email, companyId, Role.CUSTOMER);
    }

    private User newUserWithEmail(String email, Long companyId, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("{noop}test");   // gia tri bat ky: cac test nay goi thang service, khong qua dang nhap
        u.setFullName("Tester");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setCompanyId(companyId);
        return userRepository.save(u);
    }

    private Booking newPendingBooking(Long userId, String amount) {
        String s = uid();
        Booking b = new Booking();
        b.setPublicCode("TB" + s);
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTitle("Test booking " + s);
        b.setQuantity(1);
        b.setAmount(new BigDecimal(amount));
        b.setCurrency("VND");
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }
}

/**
 * MySQL 8 + Redis 7 qua Testcontainers (giong TestcontainersConfiguration co san cua project,
 * dat o day de test nay tu chua trong package corporate). Can Docker dang chay.
 */
@TestConfiguration(proxyBeanMethods = false)
class CorporateTestContainers {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    }
}
