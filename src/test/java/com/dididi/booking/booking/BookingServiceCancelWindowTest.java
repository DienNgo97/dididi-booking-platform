package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BP-BK-06: withinCancelWindow FAIL CLOSED — moc tham chieu null (thieu ngay/gio) -> coi nhu NGOAI cua so.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceCancelWindowTest {

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
    @Mock com.dididi.booking.ops.service.OpsAlertService opsAlerts;

    BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, flightRepository, hotelRepository, flightAdapter,
                pmsAdapter, roomTypeRepository, roomInventoryRepository, emailService, loyaltyService,
                groupBookingRepository, userNotificationService, opsAlerts);
    }

    @Test
    void hotelWithNullCheckIn_failsClosed_outsideWindow() {
        Booking b = new Booking();
        b.setType(BookingType.HOTEL);
        b.setCheckIn(null);   // thieu ngay -> cancelReferenceTime() = null

        assertThat(service.cancelReferenceTime(b)).isNull();
        assertThat(service.cancelDeadline(b)).isNull();
        // FAIL CLOSED: khong xac dinh duoc moc -> KHONG cho tu huy (truoc day tra true = bo qua chinh sach 48h).
        assertThat(service.withinCancelWindow(b)).isFalse();
    }

    @Test
    void flightWithNullTravelDate_failsClosed_outsideWindow() {
        Booking b = new Booking();
        b.setType(BookingType.FLIGHT);
        b.setTravelDate(null);

        assertThat(service.withinCancelWindow(b)).isFalse();
    }

    @Test
    void hotelFarInFuture_remainsWithinWindow() {
        Booking b = new Booking();
        b.setType(BookingType.HOTEL);
        b.setCheckIn(LocalDate.now().plusDays(30));   // con xa hon 48h -> trong cua so

        assertThat(service.cancelDeadline(b)).isNotNull();
        assertThat(service.withinCancelWindow(b)).isTrue();
    }

    @Test
    void hotelInPast_outsideWindow() {
        Booking b = new Booking();
        b.setType(BookingType.HOTEL);
        b.setCheckIn(LocalDate.now().minusDays(1));   // deadline da qua

        LocalDateTime deadline = service.cancelDeadline(b);
        assertThat(deadline).isNotNull();
        assertThat(service.withinCancelWindow(b)).isFalse();
    }
}
