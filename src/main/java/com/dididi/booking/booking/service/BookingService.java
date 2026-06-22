package com.dididi.booking.booking.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.CancelStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.group.repository.GroupBookingRepository;
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
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // Quy uoc qua dem mac dinh + thoi gian don phong giua 2 luot dat (phut).
    private static final LocalTime OVERNIGHT_CHECKIN = LocalTime.of(14, 0);   // nhan phong 14:00
    private static final LocalTime OVERNIGHT_CHECKOUT = LocalTime.of(12, 0);  // tra phong 12:00
    private static final long CLEAN_BUFFER_MINUTES = 120;                     // 2 gio don phong
    private static final long CANCEL_CUTOFF_HOURS = 48;                       // chi tu huy khi con > 48h truoc nhan phong/khoi hanh
    public static final int HOLD_MINUTES = 20;                                // giu phong toi da 20' cho thanh toan
    // Chuyen sync tu flight-provider co externalId nho (1..230); chuyen demo cuc bo dung 900000+ (xem DemoDataSeeder).
    // Ve cuc bo duoc dat THANG (giong khach san DIRECT bo qua PMS), khong goi provider.
    private static final long LOCAL_FLIGHT_EXTERNAL_ID_BASE = 900_000L;

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final MockFlightProviderAdapter flightAdapter;
    private final PmsApiAdapter pmsAdapter;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final EmailService emailService;
    private final GroupBookingRepository groupBookingRepository;
    private final com.dididi.booking.loyalty.service.LoyaltyService loyaltyService;

    public BookingService(BookingRepository bookingRepository, FlightRepository flightRepository,
                          HotelRepository hotelRepository, MockFlightProviderAdapter flightAdapter,
                          PmsApiAdapter pmsAdapter, RoomTypeRepository roomTypeRepository,
                          RoomInventoryRepository roomInventoryRepository, EmailService emailService,
                          com.dididi.booking.loyalty.service.LoyaltyService loyaltyService,
                          GroupBookingRepository groupBookingRepository) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
        this.flightAdapter = flightAdapter;
        this.pmsAdapter = pmsAdapter;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
        this.emailService = emailService;
        this.loyaltyService = loyaltyService;
        this.groupBookingRepository = groupBookingRepository;
    }

    public Booking createFlightBooking(Long userId, Long flightId, String passengerName,
                                       String contactEmail, int seats,
                                       String passengersText, BigDecimal extras) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new BusinessException("FLIGHT_NOT_FOUND", "Không tìm thấy chuyến bay", HttpStatus.NOT_FOUND));

        String confirmation;
        BigDecimal amount;
        String currency = f.getCurrency();

        if (isProviderFlight(f)) {
            // Chuyen da dong bo voi flight-provider -> dat qua provider (REST + retry + circuit breaker).
            FlightBookResult res;
            try {
                res = flightAdapter.bookFlight(f.getExternalId(), passengerName, contactEmail, seats);
            } catch (Exception ex) {
                throw new BusinessException("PROVIDER_ERROR", "Không đặt được vé (nhà cung cấp lỗi): " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
            }
            confirmation = res != null ? res.confirmationCode() : null;
            amount = res != null ? res.totalPrice() : null;
            if (res != null && res.currency() != null) currency = res.currency();
        } else {
            // Chuyen bay CUC BO (catalog demo, externalId >= 900000 hoac chua dong bo provider):
            // dat THANG giong khach san DIRECT bo qua PMS -> khong goi provider, khong dinh circuit breaker.
            Integer avail = f.getAvailableSeats();
            if (avail != null && avail < seats) {
                throw new BusinessException("FLIGHT_SOLD_OUT", "Chuyến bay không đủ ghế trống", HttpStatus.CONFLICT);
            }
            confirmation = "LCL-" + generateCode();
            amount = f.getPrice() != null ? f.getPrice().multiply(BigDecimal.valueOf(seats)) : null;
        }

        Booking b = new Booking();
        b.setPublicCode(generateCode());
        b.setUserId(userId);
        b.setType(BookingType.FLIGHT);
        b.setTargetId(f.getId());
        b.setTitle(safe(f.getAirlineCode()) + safe(f.getFlightNumber()) + " "
                + safe(f.getFromAirport()) + "→" + safe(f.getToAirport()));
        b.setProviderConfirmation(confirmation);
        b.setTravelDate(f.getDepartureTime());
        b.setQuantity(seats);
        if (extras != null && amount != null) amount = amount.add(extras);
        b.setAmount(amount);
        b.setPassengers(passengersText);
        applyTierDiscount(b);
        if (currency != null) b.setCurrency(currency);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /** Chuyen bay co backing o flight-provider (externalId 1..900000) -> dat qua provider; con lai dat cuc bo. */
    private boolean isProviderFlight(Flight f) {
        return f.getExternalId() != null && f.getExternalId() < LOCAL_FLIGHT_EXTERNAL_ID_BASE;
    }

    /**
     * Dat ve CO CHON CHO NGOI (chi cho chuyen da dong bo voi flight-provider).
     * Giu cho {@code HOLD_MINUTES} phut o phia provider (holdRef = ma don) -> tao don PENDING_PAYMENT.
     * Het han chua thanh toan: provider tu nha ghe; ngoai ra markPaymentExpired/cancel cung goi release.
     */
    @Transactional
    public Booking createFlightBookingWithSeats(Long userId, Long flightId, String passengerName,
                                                String contactEmail, java.util.List<String> seatCodes,
                                                String passengersText, BigDecimal extras) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new BusinessException("FLIGHT_NOT_FOUND", "Không tìm thấy chuyến bay", HttpStatus.NOT_FOUND));
        if (!isProviderFlight(f)) {
            throw new BusinessException("SEAT_UNSUPPORTED",
                    "Chuyến bay này chưa hỗ trợ chọn chỗ ngồi", HttpStatus.CONFLICT);
        }
        if (seatCodes == null || seatCodes.isEmpty()) {
            throw new BusinessException("NO_SEAT", "Vui lòng chọn ít nhất 1 ghế", HttpStatus.BAD_REQUEST);
        }

        String code = generateCode();
        com.dididi.booking.integration.dto.SeatHoldResult hold;
        try {
            hold = flightAdapter.holdSeats(f.getExternalId(), seatCodes, code, HOLD_MINUTES);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            throw new BusinessException("SEAT_TAKEN",
                    "Một số ghế vừa có người chọn, vui lòng chọn lại.", HttpStatus.CONFLICT);
        } catch (Exception ex) {
            throw new BusinessException("PROVIDER_ERROR",
                    "Không giữ được chỗ (nhà cung cấp lỗi): " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        StringBuilder codes = new StringBuilder();
        for (com.dididi.booking.integration.dto.SeatItem s : hold.seats()) {
            if (codes.length() > 0) codes.append(",");
            codes.append(s.code());
        }

        Booking b = new Booking();
        b.setPublicCode(code);
        b.setUserId(userId);
        b.setType(BookingType.FLIGHT);
        b.setTargetId(f.getId());
        b.setTitle(safe(f.getAirlineCode()) + safe(f.getFlightNumber()) + " "
                + safe(f.getFromAirport()) + "→" + safe(f.getToAirport()) + " [" + codes + "]");
        b.setTravelDate(f.getDepartureTime());
        b.setQuantity(hold.seats().size());
        b.setSeatCodes(codes.toString());
        BigDecimal total = hold.totalPrice();
        if (extras != null && total != null) total = total.add(extras);
        b.setAmount(total);
        b.setPassengers(passengersText);
        applyTierDiscount(b);
        String cur = (hold.currency() != null) ? hold.currency() : f.getCurrency();
        if (cur != null) b.setCurrency(cur);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /** Xac nhan ghe voi flight-provider khi thanh toan thanh cong (chi don ve co chon cho). Best-effort. */
    private void confirmFlightSeats(Booking b) {
        if (b == null || b.getType() != BookingType.FLIGHT
                || b.getSeatCodes() == null || b.getSeatCodes().isBlank() || b.getTargetId() == null) {
            return;
        }
        flightRepository.findById(b.getTargetId()).ifPresent(f -> {
            if (f.getExternalId() == null) return;
            try {
                flightAdapter.confirmSeats(f.getExternalId(), b.getPublicCode());
            } catch (Exception ex) {
                log.warn("Confirm seats failed for {}: {}", b.getPublicCode(), ex.toString());
            }
        });
    }

    /** Nha ghe ve flight-provider khi huy/het han thanh toan (chi don ve co chon cho). Best-effort. */
    private void releaseFlightSeats(Booking b) {
        if (b == null || b.getType() != BookingType.FLIGHT
                || b.getSeatCodes() == null || b.getSeatCodes().isBlank() || b.getTargetId() == null) {
            return;
        }
        flightRepository.findById(b.getTargetId()).ifPresent(f -> {
            if (f.getExternalId() == null) return;
            try {
                flightAdapter.releaseSeats(f.getExternalId(), b.getPublicCode());
            } catch (Exception ex) {
                log.warn("Release seats failed for {}: {}", b.getPublicCode(), ex.toString());
            }
        });
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
        applyTierDiscount(b);
        if (res != null && res.currency() != null) b.setCurrency(res.currency());
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /** Dat phong qua dem cho khach san DIRECT: kiem tra con phong theo khung gio (14:00 -> 12:00 + 2h don phong). */
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

        // 1) Kiem tra con phong theo khung gio thuc (qua dem 14:00 -> 12:00, + 2h don phong).
        LocalDateTime[] iv = occupancyInterval(false, checkIn, checkOut, null, null);
        ensureRoomAvailable(rt, iv[0], iv[1], rooms);

        // 2) Tinh tong tien theo tung dem (giu gia override theo dem neu vendor co dat).
        BigDecimal amount = BigDecimal.ZERO;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), d).orElse(null);
            BigDecimal nightPrice = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
            amount = amount.add(nightPrice.multiply(BigDecimal.valueOf(rooms)));
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
        applyTierDiscount(b);
        b.setCurrency(rt.getCurrency() != null ? rt.getCurrency() : "VND");
        b.setRoomTypeId(rt.getId());   // de tinh con phong theo khung gio + review
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /**
     * Dat phong theo gio (cho o trong ngay) cho khach san DIRECT.
     * Quy tac gia: luu tru <= 4 gio -> nua gia phong; tren 4 gio -> full gia phong.
     * Con phong tinh theo khung gio thuc (cong 2h don phong giua cac luot dat).
     */
    @Transactional
    public Booking createDayUseHotelBooking(Long userId, Long hotelId, Long roomTypeId, String roomName,
                                            String guestName, LocalDate date, LocalTime timeIn, LocalTime timeOut, int rooms) {
        Hotel h = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
        if (h.getSource() != HotelSource.DIRECT) {
            throw new BusinessException("DAY_USE_UNSUPPORTED",
                    "Khách sạn này chưa hỗ trợ đặt theo giờ (chỗ ở trong ngày)", HttpStatus.CONFLICT);
        }
        if (date == null || timeIn == null || timeOut == null || !timeOut.isAfter(timeIn)) {
            throw new BusinessException("BAD_TIMES", "Giờ trả phòng phải sau giờ nhận phòng", HttpStatus.BAD_REQUEST);
        }
        if (rooms < 1) {
            throw new BusinessException("BAD_ROOMS", "Số phòng phải >= 1", HttpStatus.BAD_REQUEST);
        }
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "Không tìm thấy loại phòng", HttpStatus.NOT_FOUND));
        if (!rt.getHotelId().equals(h.getId())) {
            throw new BusinessException("ROOM_MISMATCH", "Loại phòng không thuộc khách sạn này", HttpStatus.BAD_REQUEST);
        }

        // 1) Kiem tra con phong theo khung gio [date timeIn, date timeOut + 2h don phong].
        LocalDateTime[] iv = occupancyInterval(true, date, date, timeIn, timeOut);
        ensureRoomAvailable(rt, iv[0], iv[1], rooms);

        // 2) Tinh tien theo so gio luu tru: <= 4 gio (240') -> nua gia; > 4 gio -> full gia.
        RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), date).orElse(null);
        BigDecimal base = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
        long minutes = Duration.between(timeIn, timeOut).toMinutes();
        BigDecimal factor = (minutes <= 240) ? new BigDecimal("0.5") : BigDecimal.ONE;
        BigDecimal amount = base.multiply(factor).multiply(BigDecimal.valueOf(rooms))
                .setScale(0, RoundingMode.HALF_UP);

        // 3) Tao booking trong ngay.
        Booking b = new Booking();
        b.setPublicCode(generateCode());
        b.setUserId(userId);
        b.setType(BookingType.HOTEL);
        b.setTargetId(h.getId());
        String label = (roomName != null && !roomName.isBlank()) ? roomName : rt.getName();
        b.setTitle(h.getName() + " — " + label + " (Trong ngày)");
        b.setCheckIn(date);
        b.setCheckOut(date);
        b.setDayUse(true);
        b.setCheckInTime(timeIn);
        b.setCheckOutTime(timeOut);
        b.setQuantity(rooms);
        b.setAmount(amount);
        applyTierDiscount(b);
        b.setCurrency(rt.getCurrency() != null ? rt.getCurrency() : "VND");
        b.setRoomTypeId(rt.getId());
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return bookingRepository.save(b);
    }

    /** Sua don khach san DIRECT QUA DEM dang cho thanh toan: doi ngay nhan/tra + so phong. */
    @Transactional
    public Booking editDirectOvernight(String publicCode, Long userId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        Booking b = getForUser(publicCode, userId);
        requireEditableDirectHotel(b, false);
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BusinessException("BAD_DATES", "Ngày trả phòng phải sau ngày nhận phòng", HttpStatus.BAD_REQUEST);
        }
        if (rooms < 1) {
            throw new BusinessException("BAD_ROOMS", "Số phòng phải >= 1", HttpStatus.BAD_REQUEST);
        }
        RoomType rt = roomTypeRepository.findById(b.getRoomTypeId())
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "Không tìm thấy loại phòng", HttpStatus.NOT_FOUND));

        LocalDateTime[] iv = occupancyInterval(false, checkIn, checkOut, null, null);
        ensureRoomAvailable(rt, iv[0], iv[1], rooms, b.getId());   // loai tru chinh don dang sua

        BigDecimal gross = BigDecimal.ZERO;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), d).orElse(null);
            BigDecimal nightPrice = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
            gross = gross.add(nightPrice.multiply(BigDecimal.valueOf(rooms)));
        }

        b.setCheckIn(checkIn);
        b.setCheckOut(checkOut);
        b.setQuantity(rooms);
        resetDiscountsAndPrice(b, gross);
        return bookingRepository.save(b);
    }

    /** Sua don khach san DIRECT THEO GIO (trong ngay) dang cho thanh toan: doi ngay/gio + so phong. */
    @Transactional
    public Booking editDirectDayUse(String publicCode, Long userId, LocalDate date, LocalTime timeIn, LocalTime timeOut, int rooms) {
        Booking b = getForUser(publicCode, userId);
        requireEditableDirectHotel(b, true);
        if (date == null || timeIn == null || timeOut == null || !timeOut.isAfter(timeIn)) {
            throw new BusinessException("BAD_TIMES", "Giờ trả phòng phải sau giờ nhận phòng", HttpStatus.BAD_REQUEST);
        }
        if (rooms < 1) {
            throw new BusinessException("BAD_ROOMS", "Số phòng phải >= 1", HttpStatus.BAD_REQUEST);
        }
        RoomType rt = roomTypeRepository.findById(b.getRoomTypeId())
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "Không tìm thấy loại phòng", HttpStatus.NOT_FOUND));

        LocalDateTime[] iv = occupancyInterval(true, date, date, timeIn, timeOut);
        ensureRoomAvailable(rt, iv[0], iv[1], rooms, b.getId());

        RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), date).orElse(null);
        BigDecimal base = (inv != null && inv.getPrice() != null) ? inv.getPrice() : rt.getBasePrice();
        long minutes = Duration.between(timeIn, timeOut).toMinutes();
        BigDecimal factor = (minutes <= 240) ? new BigDecimal("0.5") : BigDecimal.ONE;
        BigDecimal gross = base.multiply(factor).multiply(BigDecimal.valueOf(rooms)).setScale(0, RoundingMode.HALF_UP);

        b.setCheckIn(date);
        b.setCheckOut(date);
        b.setCheckInTime(timeIn);
        b.setCheckOutTime(timeOut);
        b.setQuantity(rooms);
        resetDiscountsAndPrice(b, gross);
        return bookingRepository.save(b);
    }

    /** Chi cho sua don DIRECT hotel (co roomTypeId) dang PENDING_PAYMENT, dung loai overnight/day-use. */
    private void requireEditableDirectHotel(Booking b, boolean dayUse) {
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("CANNOT_EDIT", "Chỉ sửa được đơn đang chờ thanh toán", HttpStatus.CONFLICT);
        }
        if (b.getType() != BookingType.HOTEL || b.getRoomTypeId() == null) {
            throw new BusinessException("CANNOT_EDIT",
                    "Đơn này (vé máy bay/khách sạn đối tác) không hỗ trợ chỉnh sửa", HttpStatus.CONFLICT);
        }
        if (b.isDayUse() != dayUse) {
            throw new BusinessException("CANNOT_EDIT", "Loại hình lưu trú không khớp", HttpStatus.BAD_REQUEST);
        }
    }

    /** Sau khi doi ngay/gio/so phong: bo voucher dang ap, dat lai gia goc roi ap uu dai hang theo gia moi. */
    private void resetDiscountsAndPrice(Booking b, BigDecimal gross) {
        b.setVoucherCode(null);
        b.setDiscountAmount(null);
        b.setOriginalAmount(null);
        b.setAmount(gross);
        applyTierDiscount(b);
    }

    /**
     * Chot gia sau khi da tinh gross (= b.getAmount()): ap uu dai giam gia theo HANG cua khach.
     * Tier discount tinh tren gia goc, doc lap voucher. SILVER -> 0 (khong doi gia).
     * Luu tier + tierDiscountAmount; neu co giam thi set originalAmount = gross va amount = gross - td.
     */
    private void applyTierDiscount(Booking b) {
        BigDecimal gross = b.getAmount();
        if (gross == null || b.getUserId() == null) return;
        b.setTier(loyaltyService.tier(b.getUserId()));
        BigDecimal td = loyaltyService.tierDiscount(b.getUserId(), gross);
        b.setTierDiscountAmount(td);
        if (td.signum() > 0) {
            b.setOriginalAmount(gross);
            b.setAmount(gross.subtract(td));
        }
    }

    public Booking getForUser(String publicCode, Long userId) {
        Booking b = bookingRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (!b.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền", HttpStatus.FORBIDDEN);
        }
        return b;
    }

    /**
     * Cho phep CHU SO HUU don HOAC CHU NHOM (neu don thuoc 1 nhom) truy cap don.
     * Dung cho luong "chu nhom thanh toan cho ca nhom".
     */
    public Booking getForUserOrGroupOrganizer(String publicCode, Long userId) {
        Booking b = bookingRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy đơn", HttpStatus.NOT_FOUND));
        if (userId != null && userId.equals(b.getUserId())) {
            return b;                                   // chu so huu don
        }
        if (b.getGroupId() != null && userId != null) { // hoac chu nhom cua don do
            boolean organizer = groupBookingRepository.findById(b.getGroupId())
                    .map(g -> userId.equals(g.getOrganizerUserId()))
                    .orElse(false);
            if (organizer) return b;
        }
        throw new BusinessException("FORBIDDEN", "Không có quyền", HttpStatus.FORBIDDEN);
    }

    public List<Booking> myBookings(Long userId) {
        return myBookings(userId, null, null);
    }

    /** Danh sach don cua khach, loc theo loai (HOTEL/FLIGHT) va/hoac trang thai (neu khac null). */
    public List<Booking> myBookings(Long userId, BookingType type, BookingStatus status) {
        List<Booking> all = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (type == null && status == null) return all;
        List<Booking> out = new ArrayList<>();
        for (Booking b : all) {
            if (type != null && b.getType() != type) continue;
            if (status != null && b.getStatus() != status) continue;
            out.add(b);
        }
        return out;
    }

    @Transactional
    public Booking cancel(String publicCode, Long userId) {
        Booking b = getForUser(publicCode, userId);
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT || b.getStatus() == BookingStatus.CONFIRMED) {
            restoreDirectInventory(b);
            releaseFlightSeats(b);   // tra ghe dang giu ve flight-provider (don ve co chon cho)
        }
        b.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(b);
    }

    /**
     * Khach gui YEU CAU huy don (kem ly do) -> cho admin duyet. KHONG huy ngay, khong hoan tien.
     * Chi ap dung cho don da CONFIRMED (da thanh toan). Don bi tu choi truoc do co the gui lai.
     */
    public Booking requestCancel(String publicCode, Long userId, String reason) {
        Booking b = getForUser(publicCode, userId);
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("CANNOT_CANCEL",
                    "Chỉ có thể yêu cầu huỷ đơn đã xác nhận.", HttpStatus.CONFLICT);
        }
        if (b.getCancelStatus() == CancelStatus.REQUESTED) {
            throw new BusinessException("ALREADY_REQUESTED",
                    "Đơn đã có yêu cầu huỷ đang chờ duyệt.", HttpStatus.CONFLICT);
        }
        if (b.getCancelStatus() == CancelStatus.APPROVED) {
            throw new BusinessException("ALREADY_CANCELLED",
                    "Đơn đã được huỷ.", HttpStatus.CONFLICT);
        }
        if (!withinCancelWindow(b)) {
            throw new BusinessException("TOO_LATE_TO_CANCEL",
                    "Đơn chỉ huỷ trực tuyến được khi còn hơn 48 giờ trước giờ nhận phòng/khởi hành. "
                    + "Vui lòng liên hệ hỗ trợ khách hàng để được giúp đỡ.", HttpStatus.CONFLICT);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED",
                    "Vui lòng nhập lý do huỷ đơn.", HttpStatus.BAD_REQUEST);
        }
        b.setCancelStatus(CancelStatus.REQUESTED);
        b.setCancelReason(reason.trim());
        b.setCancelAdminNote(null);   // xoa ghi chu tu choi cu neu gui lai
        return bookingRepository.save(b);
    }

    // ===== Chinh sach huy 48h (Nhom 1+2) =====

    /** Moc thoi gian tham chieu de tinh han huy: KS = gio nhan phong (14:00, hoac gio dat theo gio); ve = travelDate. null neu thieu du lieu. */
    public LocalDateTime cancelReferenceTime(Booking b) {
        if (b.getType() == BookingType.FLIGHT) {
            return b.getTravelDate();
        }
        if (b.getCheckIn() == null) return null;
        LocalTime t = (b.isDayUse() && b.getCheckInTime() != null) ? b.getCheckInTime() : OVERNIGHT_CHECKIN;
        return b.getCheckIn().atTime(t);
    }

    /** Han cuoi duoc tu huy = moc tham chieu − 48h. null neu khong xac dinh duoc moc. */
    public LocalDateTime cancelDeadline(Booking b) {
        LocalDateTime ref = cancelReferenceTime(b);
        return ref == null ? null : ref.minusHours(CANCEL_CUTOFF_HOURS);
    }

    /** Con trong cua so tu huy khong (chi xet moc 48h). Khong xac dinh duoc moc -> coi nhu con (khong chan). */
    public boolean withinCancelWindow(Booking b) {
        LocalDateTime deadline = cancelDeadline(b);
        return deadline == null || LocalDateTime.now().isBefore(deadline);
    }

    public Booking markConfirmed(Booking b) {
        confirmFlightSeats(b);   // xac nhan ghe voi flight-provider (don ve co chon cho)
        b.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(b);
        emailService.sendBookingConfirmed(saved, LocaleContextHolder.getLocale());   // email xac nhan (phong thu, khong lam hong luong)
        try {
            loyaltyService.earnForBooking(saved);    // tich diem (idempotent; loi khong lam hong xac nhan)
        } catch (Exception ex) {
            log.warn("Loyalty earn failed for booking {}: {}", saved.getPublicCode(), ex.toString());
        }
        return saved;
    }

    /**
     * Truoc day cong tra counter ton kho khi huy/hoan tien. Hien KHONG con counter:
     * con phong duoc tinh truc tiep tu cac don dang active (PENDING_PAYMENT/CONFIRMED) theo khung gio,
     * nen chi can doi status sang CANCELLED/FAILED la phong tu dong duoc giai phong.
     * Giu ham (no-op) de cac noi goi cu (cancel/refund/admin) khong phai sua.
     */
    public void restoreDirectInventory(Booking b) {
        // no-op: xem giai thich tren.
    }

    /**
     * Khoang thoi gian phong bi giu cho 1 don, DA cong them buffer don phong o cuoi.
     *  - Qua dem: [ngayNhan 14:00, ngayTra 12:00 + 2h]
     *  - Trong ngay: [ngay timeIn, ngay timeOut + 2h]
     */
    private LocalDateTime[] occupancyInterval(boolean dayUse, LocalDate checkIn, LocalDate checkOut,
                                              LocalTime timeIn, LocalTime timeOut) {
        LocalDateTime start, end;
        if (dayUse) {
            start = checkIn.atTime(timeIn != null ? timeIn : OVERNIGHT_CHECKIN);
            end = checkIn.atTime(timeOut != null ? timeOut : OVERNIGHT_CHECKOUT);
        } else {
            start = checkIn.atTime(OVERNIGHT_CHECKIN);
            end = checkOut.atTime(OVERNIGHT_CHECKOUT);
        }
        return new LocalDateTime[]{start, end.plusMinutes(CLEAN_BUFFER_MINUTES)};
    }

    /**
     * Dam bao loai phong rt con du {rooms} phong trong khung gio [start, end) (end da gom 2h don phong).
     * Con phong = totalRooms − so phong dang bi cac don active (PENDING_PAYMENT/CONFIRMED) giu CHONG LAN
     * khung gio nay. Khong dung counter; tinh truc tiep tu cac booking.
     */
    private void ensureRoomAvailable(RoomType rt, LocalDateTime start, LocalDateTime end, int rooms) {
        ensureRoomAvailable(rt, start, end, rooms, null);
    }

    private void ensureRoomAvailable(RoomType rt, LocalDateTime start, LocalDateTime end, int rooms, Long excludeBookingId) {
        long ns = start.toEpochSecond(ZoneOffset.UTC);
        long ne = end.toEpochSecond(ZoneOffset.UTC);
        List<Booking> active = bookingRepository.findActiveForRoomType(rt.getId(),
                start.toLocalDate().minusDays(1), end.toLocalDate().plusDays(1),
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));

        // Sweep line: gom su kien (+rooms tai luc nhan, −rooms tai luc tra) trong khung [ns, ne).
        List<long[]> events = new ArrayList<>();
        addEvents(events, start, end, rooms, ns, ne);                // don dang xet
        for (Booking b : active) {
            if (excludeBookingId != null && excludeBookingId.equals(b.getId())) continue; // bo qua chinh don dang sua
            if (isPaymentExpired(b)) continue;   // don cho thanh toan qua 20' -> khong con giu cho
            LocalDateTime[] iv = occupancyInterval(b.isDayUse(), b.getCheckIn(), b.getCheckOut(),
                    b.getCheckInTime(), b.getCheckOutTime());
            addEvents(events, iv[0], iv[1], b.getQuantity(), ns, ne);
        }
        // Cung moc thoi gian: xu ly −rooms truoc +rooms (don tra dung luc don khac nhan -> khong trung).
        events.sort((a, c) -> a[0] != c[0] ? Long.compare(a[0], c[0]) : Long.compare(a[1], c[1]));
        long cur = 0, peak = 0;
        for (long[] e : events) { cur += e[1]; if (cur > peak) peak = cur; }
        if (peak > rt.getTotalRooms()) {
            throw new BusinessException("SOLD_OUT",
                    "Không đủ phòng trống cho khung giờ đã chọn (đã tính 2 giờ dọn phòng giữa các lượt đặt)",
                    HttpStatus.CONFLICT);
        }
    }

    /** Them cap su kien (+q tai s, −q tai e) sau khi cat ve khung [clipFrom, clipTo). Bo qua neu khong giao. */
    private void addEvents(List<long[]> events, LocalDateTime s, LocalDateTime e, int q, long clipFrom, long clipTo) {
        long ss = Math.max(s.toEpochSecond(ZoneOffset.UTC), clipFrom);
        long ee = Math.min(e.toEpochSecond(ZoneOffset.UTC), clipTo);
        if (ss >= ee) return;
        events.add(new long[]{ss, q});
        events.add(new long[]{ee, -q});
    }

    /** True neu don dang cho thanh toan (PENDING_PAYMENT) nhung da qua 20 phut giu phong. */
    public boolean isPaymentExpired(Booking b) {
        return b.getStatus() == BookingStatus.PENDING_PAYMENT
                && b.getCreatedAt() != null
                && b.getCreatedAt().plusSeconds(HOLD_MINUTES * 60L).isBefore(Instant.now());
    }

    /** So giay con lai cua cua so giu phong (0 neu da het). */
    public long remainingHoldSeconds(Booking b) {
        if (b.getCreatedAt() == null) return 0;
        long left = b.getCreatedAt().plusSeconds(HOLD_MINUTES * 60L).getEpochSecond()
                - Instant.now().getEpochSecond();
        return Math.max(0, left);
    }

    /** Danh dau don het han thanh toan (chuyen FAILED) -> tu dong nha phong cho khach khac. */
    public void markPaymentExpired(Booking b) {
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
            releaseFlightSeats(b);   // nha ghe ngay (provider scheduler cung tu nha sau 20')
            b.setStatus(BookingStatus.FAILED);
            bookingRepository.save(b);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder("DD-");
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }
}
