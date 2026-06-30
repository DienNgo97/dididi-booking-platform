package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.group.repository.GroupBookingRepository;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomInventoryRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.loyalty.service.LoyaltyService;
import com.dididi.booking.notification.EmailService;
import com.dididi.booking.integration.service.MockFlightProviderAdapter;
import com.dididi.booking.integration.service.PmsApiAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * markConfirmed idempotency (BP-PAY-03): goi 2 lan (cua so VNPay return vs IPN) khong gui email trung /
 * cong diem trung / xac nhan ghe lan 2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkConfirmedIdempotentTest {

    @Mock BookingRepository bookingRepository;
    @Mock FlightRepository flightRepository;
    @Mock HotelRepository hotelRepository;
    @Mock MockFlightProviderAdapter flightAdapter;
    @Mock PmsApiAdapter pmsAdapter;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock RoomInventoryRepository roomInventoryRepository;
    @Mock EmailService emailService;
    @Mock LoyaltyService loyaltyService;
    @Mock GroupBookingRepository groupBookingRepository;
    @Mock com.dididi.booking.notification.service.UserNotificationService userNotificationService;

    BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, flightRepository, hotelRepository, flightAdapter,
                pmsAdapter, roomTypeRepository, roomInventoryRepository, emailService, loyaltyService,
                groupBookingRepository, userNotificationService);
    }

    @Test
    void markConfirmedTwice_onlyConfirmsOnce() {
        Booking b = new Booking();
        b.setPublicCode("DD-ABC123");
        b.setType(BookingType.HOTEL);            // khong co seatCodes -> confirmFlightSeats bo qua
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lan 1: PENDING_PAYMENT -> CONFIRMED (gui email + tich diem).
        service.markConfirmed(b);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Lan 2: da CONFIRMED -> phai no-op.
        service.markConfirmed(b);

        // Email + loyalty + save chi 1 lan duy nhat (khong nhan doi).
        verify(emailService, times(1)).sendBookingConfirmed(any(Booking.class), any());
        verify(loyaltyService, times(1)).earnForBooking(any(Booking.class));
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }
}
