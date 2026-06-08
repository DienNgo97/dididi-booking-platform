package com.dididi.booking.vendor.api.controller;

import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.entity.RoomInventory;
import com.dididi.booking.hotel.domain.entity.RoomType;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.hotel.repository.RoomInventoryRepository;
import com.dididi.booking.hotel.repository.RoomTypeRepository;
import com.dididi.booking.vendor.api.dto.InventoryDayDto;
import com.dididi.booking.vendor.api.dto.RoomTypeDto;
import com.dididi.booking.vendor.api.dto.RoomTypeUpsertRequest;
import com.dididi.booking.vendor.api.dto.SetInventoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Vendor - Khách sạn của tôi", description = "Cần JWT role VENDOR. Mỗi vendor quản 1 khách sạn DIRECT của mình.")
@RestController
@RequestMapping("/api/vendor/v1")
public class VendorHotelApiController {

    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomInventoryRepository roomInventoryRepository;

    public VendorHotelApiController(HotelRepository hotelRepository,
                                    RoomTypeRepository roomTypeRepository,
                                    RoomInventoryRepository roomInventoryRepository) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomInventoryRepository = roomInventoryRepository;
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null) {
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(auth.getName());
    }

    private Hotel myHotel(Authentication auth) {
        Long uid = currentUserId(auth);
        return hotelRepository.findByVendorId(uid)
                .orElseThrow(() -> new BusinessException("NO_HOTEL",
                        "Tài khoản chưa gắn khách sạn nào", HttpStatus.NOT_FOUND));
    }

    private RoomType ownedRoomType(Authentication auth, Long roomTypeId) {
        Hotel hotel = myHotel(auth);
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy loại phòng", HttpStatus.NOT_FOUND));
        if (!rt.getHotelId().equals(hotel.getId())) {
            throw new BusinessException("FORBIDDEN", "Loại phòng không thuộc khách sạn của bạn", HttpStatus.FORBIDDEN);
        }
        return rt;
    }

    @Operation(summary = "Thông tin khách sạn của vendor đang đăng nhập")
    @GetMapping("/my-hotel")
    public ApiResponse<HotelApiDto> myHotelInfo(Authentication auth) {
        return ApiResponse.ok(HotelApiDto.from(myHotel(auth)));
    }

    @Operation(summary = "Danh sách loại phòng của khách sạn")
    @GetMapping("/room-types")
    public ApiResponse<List<RoomTypeDto>> listRoomTypes(Authentication auth) {
        Hotel hotel = myHotel(auth);
        return ApiResponse.ok(roomTypeRepository.findByHotelIdOrderByBasePrice(hotel.getId())
                .stream().map(RoomTypeDto::from).toList());
    }

    @Operation(summary = "Tạo loại phòng")
    @PostMapping("/room-types")
    public ApiResponse<RoomTypeDto> createRoomType(Authentication auth, @Valid @RequestBody RoomTypeUpsertRequest req) {
        Hotel hotel = myHotel(auth);
        RoomType rt = new RoomType();
        rt.setHotelId(hotel.getId());
        apply(rt, req);
        roomTypeRepository.save(rt);
        return ApiResponse.ok(RoomTypeDto.from(rt), "Created");
    }

    @Operation(summary = "Cập nhật loại phòng")
    @PutMapping("/room-types/{id}")
    public ApiResponse<RoomTypeDto> updateRoomType(Authentication auth, @PathVariable Long id,
                                                   @Valid @RequestBody RoomTypeUpsertRequest req) {
        RoomType rt = ownedRoomType(auth, id);
        apply(rt, req);
        roomTypeRepository.save(rt);
        return ApiResponse.ok(RoomTypeDto.from(rt), "Updated");
    }

    @Operation(summary = "Xoá loại phòng (kèm toàn bộ tồn kho của nó)")
    @DeleteMapping("/room-types/{id}")
    @Transactional
    public ApiResponse<Void> deleteRoomType(Authentication auth, @PathVariable Long id) {
        RoomType rt = ownedRoomType(auth, id);
        roomInventoryRepository.deleteAll(roomInventoryRepository.findByRoomTypeId(rt.getId()));
        roomTypeRepository.deleteById(rt.getId());
        return ApiResponse.ok(null, "Deleted");
    }

    @Operation(summary = "Xem tồn kho theo ngày của 1 loại phòng trong [from, to]")
    @GetMapping("/room-types/{id}/inventory")
    public ApiResponse<List<InventoryDayDto>> getInventory(
            Authentication auth, @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoomType rt = ownedRoomType(auth, id);
        List<InventoryDayDto> rows = roomInventoryRepository
                .findByRoomTypeIdAndDateBetweenOrderByDate(rt.getId(), from, to)
                .stream().map(InventoryDayDto::from).toList();
        return ApiResponse.ok(rows);
    }

    @Operation(summary = "Đặt số phòng trống cho mọi ngày trong [from, to]")
    @PutMapping("/room-types/{id}/inventory")
    @Transactional
    public ApiResponse<List<InventoryDayDto>> setInventory(Authentication auth, @PathVariable Long id,
                                                           @Valid @RequestBody SetInventoryRequest req) {
        RoomType rt = ownedRoomType(auth, id);
        if (req.to().isBefore(req.from())) {
            throw new BusinessException("BAD_RANGE", "Ngày kết thúc phải >= ngày bắt đầu", HttpStatus.BAD_REQUEST);
        }
        for (LocalDate d = req.from(); !d.isAfter(req.to()); d = d.plusDays(1)) {
            final LocalDate day = d;
            RoomInventory inv = roomInventoryRepository.findByRoomTypeIdAndDate(rt.getId(), day)
                    .orElseGet(() -> {
                        RoomInventory n = new RoomInventory();
                        n.setRoomTypeId(rt.getId());
                        n.setDate(day);
                        return n;
                    });
            inv.setAvailableRooms(req.availableRooms());
            if (req.price() != null) { inv.setPrice(req.price()); }
            roomInventoryRepository.save(inv);
        }
        List<InventoryDayDto> rows = roomInventoryRepository
                .findByRoomTypeIdAndDateBetweenOrderByDate(rt.getId(), req.from(), req.to())
                .stream().map(InventoryDayDto::from).toList();
        return ApiResponse.ok(rows, "Updated");
    }

    private void apply(RoomType rt, RoomTypeUpsertRequest req) {
        rt.setName(req.name());
        rt.setDescription(req.description());
        rt.setCapacity(req.capacity());
        rt.setBasePrice(req.basePrice());
        rt.setCurrency(req.currency() == null || req.currency().isBlank() ? "VND" : req.currency());
        rt.setTotalRooms(req.totalRooms());
    }
}
