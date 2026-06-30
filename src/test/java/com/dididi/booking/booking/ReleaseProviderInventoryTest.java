package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingType;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.group.repository.GroupBookingRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.HotelSource;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * INT-01 / BP-BK-03: releaseProviderInventory goi dung adapter cho KS CHANNEL (PMS) va ve provider (flight).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseProviderInventoryTest {

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
    void channelHotel_callsPmsCancel() {
        Hotel h = new Hotel();
        h.setId(7L);
        h.setSource(HotelSource.CHANNEL);
        when(hotelRepository.findById(7L)).thenReturn(Optional.of(h));

        Booking b = new Booking();
        b.setType(BookingType.HOTEL);
        b.setTargetId(7L);
        b.setProviderReservationId(12345L);   // co reservationId -> phai goi PMS /cancel

        service.releaseProviderInventory(b);

        verify(pmsAdapter).cancel(12345L);
    }

    @Test
    void directHotel_doesNotCallPms() {
        Hotel h = new Hotel();
        h.setId(8L);
        h.setSource(HotelSource.DIRECT);
        when(hotelRepository.findById(8L)).thenReturn(Optional.of(h));

        Booking b = new Booking();
        b.setType(BookingType.HOTEL);
        b.setTargetId(8L);
        b.setProviderReservationId(999L);   // DIRECT thi du co id cung khong goi PMS

        service.releaseProviderInventory(b);

        verify(pmsAdapter, never()).cancel(anyLong());
    }

    @Test
    void providerFlight_callsFlightCancelBooking() {
        Flight f = new Flight();
        f.setId(3L);
        f.setExternalId(150L);   // < 900000 -> ve provider
        when(flightRepository.findById(3L)).thenReturn(Optional.of(f));

        Booking b = new Booking();
        b.setType(BookingType.FLIGHT);
        b.setTargetId(3L);
        b.setPublicCode("DD-XYZ999");
        b.setProviderConfirmation("FP-CONF-1");   // ve provider khong chon cho -> huy theo confirmationCode

        service.releaseProviderInventory(b);

        verify(flightAdapter).cancelBooking(eq(150L), eq("FP-CONF-1"));
    }

    @Test
    void localFlight_doesNotCallProviderCancel() {
        Flight f = new Flight();
        f.setId(4L);
        f.setExternalId(900_005L);   // ve cuc bo -> khong co provider de huy
        when(flightRepository.findById(4L)).thenReturn(Optional.of(f));

        Booking b = new Booking();
        b.setType(BookingType.FLIGHT);
        b.setTargetId(4L);
        b.setPublicCode("DD-LCL001");
        b.setProviderConfirmation("LCL-ABC");

        service.releaseProviderInventory(b);

        verify(flightAdapter, never()).cancelBooking(anyLong(), eq("LCL-ABC"));
    }
}
