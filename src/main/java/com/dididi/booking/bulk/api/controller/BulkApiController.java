package com.dididi.booking.bulk.api.controller;

import com.dididi.booking.bulk.api.dto.BulkLineResult;
import com.dididi.booking.bulk.service.BulkBookingService;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Đặt hàng loạt (nhiều phòng cùng khách sạn/loại phòng) cho khách — bản REST của web /booking/bulk.
 * Body: { hotelId, roomTypeId, roomName?, stay:"overnight"|"day",
 *         checkIn, checkOut  (overnight)  |  date, timeIn, timeOut (day),
 *         guests: [ { name, rooms? } ] }
 */
@Tag(name = "Bulk booking (khách)")
@RestController
@RequestMapping("/api/v1/bulk")
public class BulkApiController {

    private final BulkBookingService bulkService;

    public BulkApiController(BulkBookingService bulkService) {
        this.bulkService = bulkService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @SuppressWarnings("unchecked")
    @Operation(summary = "Đặt hàng loạt nhiều phòng (mỗi khách 1 dòng)")
    @PostMapping("/hotel")
    public ApiResponse<List<BulkLineResult>> createHotelBulk(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = uid(auth);
        Long hotelId = Long.valueOf(str(body.get("hotelId")));
        Long roomTypeId = Long.valueOf(str(body.get("roomTypeId")));
        String roomName = body.get("roomName") == null ? null : str(body.get("roomName"));
        String stay = body.get("stay") == null ? "overnight" : str(body.get("stay"));
        boolean dayUse = "day".equalsIgnoreCase(stay);

        List<Map<String, Object>> guests = (List<Map<String, Object>>) body.getOrDefault("guests", List.of());
        List<String> names = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        for (Map<String, Object> gg : guests) {
            names.add(str(gg.get("name")));
            counts.add(gg.get("rooms") == null ? "1" : str(gg.get("rooms")));
        }

        List<String> checkIns = new ArrayList<>();
        List<String> checkOuts = new ArrayList<>();
        List<String> dayDates = new ArrayList<>();
        List<String> timeIns = new ArrayList<>();
        List<String> timeOuts = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (dayUse) {
                dayDates.add(str(body.get("date")));
                timeIns.add(str(body.get("timeIn")));
                timeOuts.add(str(body.get("timeOut")));
            } else {
                checkIns.add(str(body.get("checkIn")));
                checkOuts.add(str(body.get("checkOut")));
            }
        }

        List<BulkLineResult> results = bulkService.createBulk(userId, hotelId, roomTypeId, roomName, stay,
                names, checkIns, checkOuts, dayDates, timeIns, timeOuts, counts, false);
        return ApiResponse.ok(results, "Đã xử lý đặt hàng loạt");
    }
}
