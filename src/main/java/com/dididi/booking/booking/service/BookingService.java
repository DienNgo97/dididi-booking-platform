package com.dididi.booking.booking.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.notification.EmailService;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomInventory;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.domain.enums.HotelSource;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomInventoryRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.integration.dto.FlightBookResult;
import com.dididi.booking.integration.dto.ReserveResult;
import com.dididi.booking.integration.service.MockFlightProviderAdapter;
import com.dididi.booking.integration.service.PmsApiAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final MockFlightProviderAdapter flightAdapter;
    private final PmsApiAdapter pmsAdapter;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final EmailService emailService;
    private final com.dididi.booking.loyalty.service.LoyaltyService loyaltyService;

    public BookingService(BookingRepository bookingRepository, FlightRepository flightRepository,
                          HotelRepository hotelRepository, MockFlightProviderAdapter flightAdapter,
                          PmsApiAdapter pmsAdapter, RoomTypeRepository roomTypeRepository,
                          RoomInventoryRepository roomInventoryRepository, EmailService emailService,
                          com.dididi.booking.loyalty.service.LoyaltyService loyaltyService) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
        this.flightAdapter = flightAdapter;
        this.pmsAdapter = pmsAdapter;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.emailService = emailService;
        this.loyaltyService = loyaltyService;
    }

    public Booking createFlightBooking(Long userId, Long flightId, String passengerName,
                                       String contactEmail, int seats) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new BusinessException("FLIGHT_NOT_FOUND", "Không tìm thấy chuyến bay", HttpStatus.NOT_FOUND));
        if (f.getExternalId() == null) {
            throw new BusinessException("NO_EXTERNAL", "Chuyến bay chưa đồng bộ với nhà cung cấp", HttpStatus.CONFLICT);
        }
        FlightBookResult res;
        try {
            res = flightAdapter.bookFlight(f.getExternalId(), passengerName, contactEmail, seats);
        } catch (Exception ex) {
            throw new BusinessException("PROVIDER_ERROR", "Không đặt được vé (nhà cung cấp lỗi): " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        Booking b = new Booking();
        b.setPublicCode(generateCode());
        b.setUserId(userId);
        b.setType(BookingType.FLIGHT);
        b.setTargetId(f.getId());
        b.setTitle(safe(f.getAirlineCode()) + safe(f.getFlightNumber()) + " "
                + safe(f.getFromAirport()) + "→" + safe(f.getToAirport()));
        b.setProviderConfirmation(res != null ? res.confirmationCode() : null);
        b.setTravelDate(f.getDepartureTime());
        b.setQuantity(seats);
        b.setAmount(res != null ? res.totalPrice() : null);
        if (res != null && res.currency() != null) b.setCurrency(res.currency());
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /**
     * Dat phong. Re nhanh theo nguon khach san:
     *  - DIRECT  -> tru ton kho noi bo cua Dididi (khong goi PMS).
     *  - CHANNEL / cu (null) -> goi PMS adapter nhu truoc.
     */
    @Transactional
    public Booking createHotelBooking(Long userId, Long hotelId, Long roomTypeId, String roomName,
                                      String guestName, LocalDate checkIn, LocalDate checkOut, int rooms) {
        Hotel h = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BusinessException("BAD_DATES", "Ngày trả phòng phải sau ngày nhận phòng", HttpStatus.BAD_REQUEST);
        }

        if (h.getSource() == HotelSource.DIRECT) {
            return createDirectHotelBooking(userId, h, roomTypeId, roomName, checkIn, checkOut, rooms);
        }

        // CHANNEL / cu (null) -> PMS
        if (h.getExternalId() == null) {
            throw new BusinessException("NO_EXTERNAL", "Khách sạn chưa đồng bộ với PMS", HttpStatus.CONFLICT);
        }
        ReserveResult res;
        try {
            res = pmsAdapter.reserve(h.getExternalId(), roomTypeId, guestName, checkIn, checkOut, rooms);
        } catch (Exception ex) {
            throw new BusinessException("PROVIDER_ERROR", "Không đặt được phòng (PMS lỗi): " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        Booking b = new Booking();
        b.setPublicCode(generateCode());
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTargetId(h.getId());
        b.setTitle(h.getName() + (roomName != null ? " — " + roomName : ""));
        b.setProviderConfirmation(res != null ? res.confirmationCode() : null);
        b.setCheckIn(checkIn);
        b.setCheckOut(checkOut);
        b.setQuantity(rooms);
        b.setAmount(res != null ? res.totalPrice() : null);
        if (res != null && res.currency() != null) b.setCurrency(res.currency());
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /** Dat phong cho khach san DIRECT: kiem tra + tru ton kho tung dem [checkIn, checkOut). */
    private Booking createDirectHotelBooking(Long userId, Hotel h, Long roomTypeId, String roomName,
                                             LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (rooms < 1) {
            throw new BusinessException("BAD_ROOMS", "Số phòng phải >= 1", HttpStatus.BAD_REQUEST);
        }
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "Không tìm thấy loại phòng", HttpStatus.NOT_FOUND));
        if (!rt.getHotelId().equals(h.getId())) {
            throw new BusinessException("ROOM_MISMATCH", "Loại phòng không thuộc khách sạn này", HttpStatus.BAD_REQUEST);
        }

        // 1) Kiem tra ton kho tat ca cac dem; tinh tong tien.
        BigDecimal amount = BigDecimal.ZERO;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), d).orElse(null);
            int avail = (inv != null) ? inv.getAvailableRooms() : rt.getTotalRooms();
            if (avail < rooms) {
                throw new BusinessException("SOLD_OUT",
                        "Không đủ phòng trống ngày " + d + " (còn " + avail + ")", HttpStatus.CONFLICT);
            }
            BigDecimal nightPrice = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
            amount = amount.add(nightPrice.multiply(BigDecimal.valueOf(rooms)));
        }

        // 2) Tru ton kho moi dem (tao row neu chua co, mac dinh = totalRooms).
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            final LocalDate day = d;
            RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), day)
                    .orElseGet(() -> {
                        RoomInventory n = new RoomInventory();
                        n.setRoomTypeId(rt.getId());
                        n.setDate(day);
                        n.setAvailableRooms(rt.getTotalRooms());
                        return n;
                    });
            inv.setAvailableRooms(inv.getAvailableRooms() - rooms);
            roomInventoryRepository.save(inv);
        }

        // 3) Tao booking (DIRECT khong co providerConfirmation).
        Booking b = new Booking();
        b.setPublicCode(generateCode());
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTargetId(h.getId());
        String label = (roomName != null && !roomName.isBlank()) ? roomName : rt.getName();
        b.setTitle(h.getName() + " — " + label);
        b.setCheckIn(checkIn);
        b.setCheckOut(checkOut);
        b.setQuantity(rooms);
        b.setAmount(amount);
        b.setCurrency(rt.getCurrency() != null ? rt.getCurrency() : "VND");
        b.setRoomTypeId(rt.getId());   // de hoan tra ton kho khi huy/hoan tien
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    public Booking getForUser(String publicCode, Long userId) {
        Booking b = bookingRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (!b.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền", HttpStatus.FORBIDDEN);
        }
        return b;
    }

    public List<Booking> myBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Booking cancel(String publicCode, Long userId) {
        Booking b = getForUser(publicCode, userId);
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT || b.getStatus() == BookingStatus.CONFIRMED) {
            restoreDirectInventory(b);
        }
        b.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(b);
    }

    public Booking markConfirmed(Booking b) {
        b.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(b);
        emailService.sendBookingConfirmed(saved);   // email xac nhan (phong thu, khong lam hong luong)
        try {
            loyaltyService.earnForBooking(saved);    // tich diem (idempotent; loi khong lam hong xac nhan)
        } catch (Exception ex) {
            log.warn("Loyalty earn failed for booking {}: {}", saved.getPublicCode(), ex.toString());
        }
        return saved;
    }

    /**
     * Cong tra ton kho cho don khach san DIRECT: theo roomTypeId + tung dem [checkIn, checkOut) x so phong.
     * Chi goi khi don dang o trang thai active (xem cancel()/RefundService) de tranh cong tra 2 lan.
     * Don CHANNEL/flight hoac don cu (roomTypeId null) -> bo qua an toan.
     */
    public void restoreDirectInventory(Booking b) {
        if (b.getType() != BookingType.HOTEL) return;
        Long roomTypeId = b.getRoomTypeId();
        if (roomTypeId == null || b.getCheckIn() == null || b.getCheckOut() == null) return;
        int rooms = b.getQuantity();
        for (LocalDate d = b.getCheckIn(); d.isBefore(b.getCheckOut()); d = d.plusDays(1)) {
            final LocalDate day = d;
            roomInventoryRepository.findByRoomTypeIdAndDate(roomTypeId, day).ifPresent(inv -> {
                inv.setAvailableRooms(inv.getAvailableRooms() + rooms);
                roomInventoryRepository.save(inv);
            });
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder("DD-");
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }
}
