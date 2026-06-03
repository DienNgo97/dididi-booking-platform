package com.dididi.booking.booking.service;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.integration.dto.FlightBookResult;
import com.dididi.booking.integration.dto.ReserveResult;
import com.dididi.booking.integration.service.MockFlightProviderAdapter;
import com.dididi.booking.integration.service.PmsApiAdapter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final MockFlightProviderAdapter flightAdapter;
    private final PmsApiAdapter pmsAdapter;

    public BookingService(BookingRepository bookingRepository, FlightRepository flightRepository,
                          HotelRepository hotelRepository, MockFlightProviderAdapter flightAdapter,
                          PmsApiAdapter pmsAdapter) {
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.hotelRepository = hotelRepository;
        this.flightAdapter = flightAdapter;
        this.pmsAdapter = pmsAdapter;
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

    public Booking createHotelBooking(Long userId, Long hotelId, Long roomTypeId, String roomName,
                                      String guestName, LocalDate checkIn, LocalDate checkOut, int rooms) {
        Hotel h = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND));
        if (h.getExternalId() == null) {
            throw new BusinessException("NO_EXTERNAL", "Khách sạn chưa đồng bộ với PMS", HttpStatus.CONFLICT);
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BusinessException("BAD_DATES", "Ngày trả phòng phải sau ngày nhận phòng", HttpStatus.BAD_REQUEST);
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

    public Booking cancel(String publicCode, Long userId) {
        Booking b = getForUser(publicCode, userId);
        b.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(b);
    }

    public Booking markConfirmed(Booking b) {
        b.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(b);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder("DD-");
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }
}
