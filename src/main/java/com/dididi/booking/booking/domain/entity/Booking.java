package com.dididi.booking.booking.domain.entity;

import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.domain.enums.CancelStatus;
import com.dididi.booking.common.domain.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_bookings_code", columnNames = "public_code"))
public class Booking extends BaseEntity {

    @Column(name = "public_code", nullable = false, length = 20)
    private String publicCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingType type;

    @Column(length = 300)
    private String title;

    @Column(name = "provider_confirmation", length = 50)
    private String providerConfirmation;

    /** ID reservation ben PMS (hotel CHANNEL) -> de goi /cancel khi huy/hoan tien (INT-01). null voi DIRECT/flight. */
    @Column(name = "provider_reservation_id")
    private Long providerReservationId;

    /** Ma ghe da chon (vd "12A,12B") cho don ve co so do ghe; null neu dat theo so luong. */
    @Column(name = "seat_codes", length = 120)
    private String seatCodes;

    /** Thong tin tung hanh khach + dich vu (ten · ghe · suat an · hanh ly), moi hanh khach 1 dong. Chi don FLIGHT. */
    @Column(name = "passengers", length = 2000)
    private String passengers;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "travel_date")
    private LocalDateTime travelDate;

    /** Dat theo gio (cho o trong ngay). false = qua dem. */
    @Column(name = "day_use", nullable = false)
    private boolean dayUse = false;

    /** Gio nhan/tra phong khi dayUse = true (cung 1 ngay checkIn). */
    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** Gia goc truoc khi giam (voucher hoac uu dai hang). null = khong giam gia. amount = gia phai tra sau giam. */
    @Column(name = "original_amount", precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "voucher_code", length = 40)
    private String voucherCode;

    /** Hang thanh vien cua khach luc dat (SILVER/GOLD/PLATINUM/DIAMOND). */
    @Column(length = 16)
    private String tier;

    /** Giam gia theo hang (VND), da tru vao amount. 0/null neu hang Bac. */
    @Column(name = "tier_discount_amount", precision = 18, scale = 2)
    private BigDecimal tierDiscountAmount;

    @Column(length = 3)
    private String currency = "VND";

    /** Yeu cau huy don (khach gui -> admin duyet). Mac dinh NONE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_status", length = 16, nullable = false)
    private CancelStatus cancelStatus = CancelStatus.NONE;

    /** Ly do khach muon huy. */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** Ly do admin duyet/tu choi huy. */
    @Column(name = "cancel_admin_note", length = 500)
    private String cancelAdminNote;

    // DIRECT hotel only: luu de hoan tra ton kho khi huy/hoan tien. CHANNEL/flight = null.
    @Column(name = "room_type_id")
    private Long roomTypeId;

    // Doi tuong duoc dat: hotelId (don HOTEL) hoac flightId (don FLIGHT). Dung cho review/rating.
    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    /** Cong ty thanh toan (B2B) - null neu khach le tu tra. */
    @Column(name = "company_id")
    private Long companyId;

    /** Nhom dat phong (B2C) ma don nay thuoc ve; null = dat le. */
    @jakarta.persistence.Column(name = "group_id")
    private Long groupId;

    /** Ai da chi tien cho don nay (nhom: chu nhom neu tra gop, chu phong neu tu tra). Dung cho phieu chia tien nhom. */
    @jakarta.persistence.Column(name = "paid_by_user_id")
    private Long paidByUserId;

    public String getPublicCode() { return publicCode; }
    public void setPublicCode(String publicCode) { this.publicCode = publicCode; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BookingType getType() { return type; }
    public void setType(BookingType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProviderConfirmation() { return providerConfirmation; }
    public void setProviderConfirmation(String providerConfirmation) { this.providerConfirmation = providerConfirmation; }
    public Long getProviderReservationId() { return providerReservationId; }
    public void setProviderReservationId(Long providerReservationId) { this.providerReservationId = providerReservationId; }
    public String getSeatCodes() { return seatCodes; }
    public void setSeatCodes(String seatCodes) { this.seatCodes = seatCodes; }
    public String getPassengers() { return passengers; }
    public void setPassengers(String passengers) { this.passengers = passengers; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public LocalDateTime getTravelDate() { return travelDate; }
    public void setTravelDate(LocalDateTime travelDate) { this.travelDate = travelDate; }
    public boolean isDayUse() { return dayUse; }
    public void setDayUse(boolean dayUse) { this.dayUse = dayUse; }
    public LocalTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalTime checkInTime) { this.checkInTime = checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalTime checkOutTime) { this.checkOutTime = checkOutTime; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public BigDecimal getTierDiscountAmount() { return tierDiscountAmount; }
    public void setTierDiscountAmount(BigDecimal tierDiscountAmount) { this.tierDiscountAmount = tierDiscountAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getPaidByUserId() { return paidByUserId; }
    public void setPaidByUserId(Long paidByUserId) { this.paidByUserId = paidByUserId; }
    public CancelStatus getCancelStatus() { return cancelStatus; }
    public void setCancelStatus(CancelStatus cancelStatus) { this.cancelStatus = cancelStatus; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getCancelAdminNote() { return cancelAdminNote; }
    public void setCancelAdminNote(String cancelAdminNote) { this.cancelAdminNote = cancelAdminNote; }
}
