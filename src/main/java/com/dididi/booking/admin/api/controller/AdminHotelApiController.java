package com.dididi.booking.admin.api.controller;

import com.dididi.booking.audit.event.AuditEvent;
import com.dididi.booking.common.dto.ApiResponse;
import com.dididi.booking.hotel.api.dto.HotelApiDto;
import com.dididi.booking.hotel.api.dto.HotelUpsertRequest;
import com.dididi.booking.hotel.domain.HotelSupport;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.domain.enums.Amenity;
import com.dididi.booking.hotel.domain.enums.HotelTag;
import com.dididi.booking.hotel.domain.enums.PropertyType;
import com.dididi.booking.hotel.domain.enums.Region;
import com.dididi.booking.hotel.repository.HotelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Hotels", description = "Cần JWT role ADMIN/SUPER_ADMIN/VENDOR")
@RestController
@RequestMapping("/api/admin/v1/hotels")
public class AdminHotelApiController {

    private final HotelRepository hotelRepository;
    private final com.dididi.booking.search.HotelSearchIndexer searchIndexer;
    private final ApplicationEventPublisher events;

    public AdminHotelApiController(HotelRepository hotelRepository, ApplicationEventPublisher events, com.dididi.booking.search.HotelSearchIndexer searchIndexer) {
        this.searchIndexer = searchIndexer;
        this.hotelRepository = hotelRepository;
        this.events = events;
    }

    private static Long actorId(Authentication auth) {
        try { return auth == null ? null : Long.valueOf(auth.getName()); } catch (Exception e) { return null; }
    }

    @Operation(summary = "Danh sách tất cả khách sạn (admin)")
    @GetMapping
    public ApiResponse<List<HotelApiDto>> list() {
        return ApiResponse.ok(hotelRepository.findAll().stream().map(HotelApiDto::from).toList());
    }

    @Operation(summary = "Tạo khách sạn")
    @CacheEvict(value = {"hotelsByCityV2", "hotelByIdV2"}, allEntries = true)   // BP-CACHE-01: tuoi cache sau khi ghi
    @PostMapping
    public ApiResponse<HotelApiDto> create(@Valid @RequestBody HotelUpsertRequest req) {
        Hotel h = new Hotel();
        apply(h, req);
        // KS do admin tao la KS noi bo Dididi (khong gan PMS) -> DIRECT de doc phong tu DB + DAT DUOC.
        // (Mac dinh entity = CHANNEL -> detail() di tim phong o hotel-pms -> "Chua lay duoc loai phong" -> khong dat duoc.)
        h.setSource(com.dididi.booking.hotel.domain.enums.HotelSource.DIRECT);
        hotelRepository.save(h);
        searchIndexer.indexOne(h);   // TC-C-03: hien ngay trong tim kiem, khong doi re-index 15p
        return ApiResponse.ok(HotelApiDto.from(h), "Created");
    }

    @Operation(summary = "Cập nhật khách sạn")
    @CacheEvict(value = {"hotelsByCityV2", "hotelByIdV2"}, allEntries = true)   // BP-CACHE-01: tuoi cache sau khi ghi
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelApiDto>> update(@PathVariable Long id,
                                                           @Valid @RequestBody HotelUpsertRequest req) {
        return hotelRepository.findById(id)
                .map(h -> {
                    apply(h, req);
                    // P2: đánh dấu đã sửa tay -> job đồng bộ PMS không đè lại nội dung này nữa.
                    h.setManualOverride(true);
                    hotelRepository.save(h);
        searchIndexer.indexOne(h);   // TC-C-03: hien ngay trong tim kiem, khong doi re-index 15p
                    return ResponseEntity.ok(ApiResponse.ok(HotelApiDto.from(h), "Updated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Xoá khách sạn")
    @CacheEvict(value = {"hotelsByCityV2", "hotelByIdV2"}, allEntries = true)   // BP-CACHE-01: tuoi cache sau khi ghi
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication auth) {
        Hotel h = hotelRepository.findById(id).orElse(null);
        if (h == null) {
            return ResponseEntity.notFound().build();
        }
        hotelRepository.deleteById(id);
        searchIndexer.removeOne(id);   // P2: gỡ khỏi tìm kiếm ngay, đừng để kết quả "ma" tới 15 phút
        events.publishEvent(new AuditEvent(actorId(auth), "DELETE_HOTEL", "HOTEL", id,
                "Xoá khách sạn: " + h.getName()));
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    private void apply(Hotel h, HotelUpsertRequest req) {
        h.setName(req.name());
        h.setCity(req.city());
        h.setHouseNumber(req.houseNumber());
        h.setStreet(req.street());
        h.setWard(req.ward());
        h.setDistrict(req.district());
        h.setProvince(req.province());
        // address hiển thị: dùng chuỗi gửi lên, nếu trống thì ghép từ các thành phần tách
        String addr = req.address();
        if (addr == null || addr.isBlank()) {
            addr = HotelSupport.composeAddress(req.houseNumber(), req.street(), req.ward(),
                    req.district(), req.province(), req.city());
        }
        h.setAddress(addr);
        h.setLat(req.lat());
        h.setLng(req.lng());
        h.setDescription(req.description());
        h.setStarRating(req.starRating());
        h.setActive(req.active() == null || req.active());
        PropertyType pt = HotelSupport.parseEnum(PropertyType.class, req.propertyType());
        if (pt != null) h.setPropertyType(pt);
        Region rg = HotelSupport.parseEnum(Region.class, req.region());
        if (rg != null) h.setRegion(rg);
        if (req.amenities() != null) h.setAmenities(HotelSupport.parseEnumSet(Amenity.class, req.amenities()));
        if (req.tags() != null) h.setTags(HotelSupport.parseEnumSet(HotelTag.class, req.tags()));
    }
}
