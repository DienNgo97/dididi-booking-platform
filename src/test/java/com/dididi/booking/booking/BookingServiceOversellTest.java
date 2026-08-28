package com.dididi.booking.booking;

import com.dididi.booking.booking.domain.entity.Booking;
import com.dididi.booking.booking.domain.enums.BookingStatus;
import com.dididi.booking.booking.repository.BookingRepository;
import com.dididi.booking.booking.service.BookingService;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.flight.domain.entity.Flight;
import com.dididi.booking.flight.repository.FlightRepository;
import com.dididi.booking.group.repository.GroupBookingRepository;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BP-BK-01 (ve cuc bo tru ghe nguyen tu) + BP-BK-02 (DIRECT hotel oversell qua sweep-line).
 * Khong DB: dung Mockito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceOversellTest {

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

    // ---------- BP-BK-01: ve cuc bo (externalId >= 900000) ----------

    @Test
    void localFlight_soldOut_whenAtomicDecrementRowcountZero() {
        Flight f = new Flight();
        f.setId(10L);
        f.setExternalId(900_001L);            // ve cuc bo
        f.setPrice(new BigDecimal("1000000"));
        when(flightRepository.findById(10L)).thenReturn(Optional.of(f));
        // Conditional update khong tru duoc (het ghe) -> tra 0.
        when(flightRepository.decrementSeatsIfAvailable(10L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.createFlightBooking(1L, 10L, "A", "a@x.com", 2, null, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FLIGHT_SOLD_OUT");

        verify(bookingRepository, never()).save(any());     // khong tao booking khi het ve
        verify(flightAdapter, never()).bookFlight(anyLong(), any(), any(), anyInt());   // ve cuc bo khong goi provider
    }

    @Test
    void localFlight_success_decrementsSeatsAtomically() {
        Flight f = new Flight();
        f.setId(11L);
        f.setExternalId(900_002L);
        f.setPrice(new BigDecimal("500000"));
        when(flightRepository.findById(11L)).thenReturn(Optional.of(f));
        when(flightRepository.decrementSeatsIfAvailable(11L, 1)).thenReturn(1);   // tru thanh cong
        when(loyaltyService.tier(1L)).thenReturn("SILVER");
        when(loyaltyService.tierDiscount(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking b = service.createFlightBooking(1L, 11L, "A", "a@x.com", 1, null, BigDecimal.ZERO);

        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(flightRepository).decrementSeatsIfAvailable(11L, 1);   // da tru ghe (truoc day khong tru -> oversell)
        verify(bookingRepository).save(any(Booking.class));
    }

    // ---------- BP-BK-02: DIRECT hotel oversell ----------

    @Test
    void directHotel_soldOut_whenSweepPeakExceedsTotalRooms() {
        Hotel h = new Hotel();
        h.setId(5L);
        h.setName("Test Hotel");
        h.setSource(HotelSource.DIRECT);
        when(hotelRepository.findById(5L)).thenReturn(Optional.of(h));

        RoomType rt = new RoomType();
        rt.setId(50L);
        rt.setHotelId(5L);
        rt.setName("Deluxe");
        rt.setBasePrice(new BigDecimal("800000"));
        rt.setTotalRooms(1);                  // chi 1 phong
        // Khoa ghi (BP-BK-02) tra ve room type.
        when(roomTypeRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(rt));
        when(roomInventoryRepository.findByRoomTypeIdAndDate(anyLong(), any())).thenReturn(Optional.empty());

        // Da co 1 don active CONFIRMED giu phong cung khung gio -> them 1 nua => peak = 2 > 1 phong.
        Booking existing = new Booking();
        existing.setId(99L);
        existing.setRoomTypeId(50L);
        existing.setQuantity(1);
        // Ngày TƯƠNG LAI: từ 28/08 server chặn đặt cho ngày đã qua (S3), mốc cứng sẽ vỡ theo thời gian.
        LocalDate nhan = LocalDate.now().plusDays(10);
        LocalDate tra = nhan.plusDays(2);
        existing.setCheckIn(nhan);
        existing.setCheckOut(tra);
        existing.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findActiveForRoomType(eq(50L), any(), any(), any()))
                .thenReturn(List.of(existing));

        // Vao qua entry point public createHotelBooking -> DIRECT -> createDirectHotelBooking.
        assertThatThrownBy(() -> service.createHotelBooking(1L, 5L, 50L, "Deluxe", "Guest",
                nhan, tra, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOLD_OUT");

        verify(roomTypeRepository).findByIdForUpdate(50L);   // da khoa ghi truoc khi quet
        verify(bookingRepository, never()).save(any());
    }
}
