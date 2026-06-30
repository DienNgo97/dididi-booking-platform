package com.dididi.booking.config;

import com.dididi.booking.hotel.domain.CityGeo;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfill toạ độ cho các khách sạn ĐÃ seed trước đây (chưa có lat/lng) bằng cách suy ra từ thành phố.
 * Giúp tính năng bản đồ chạy được trên DB cũ mà KHÔNG cần xoá/seed lại.
 * Chỉ chạy ở môi trường demo (app.seed.demo=true) và chỉ đụng KS đang null toạ độ.
 */
@Component
@Order(100)
public class HotelGeoBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HotelGeoBackfill.class);

    @Value("${app.seed.demo:false}")
    private boolean enabled;

    private final HotelRepository hotelRepository;

    public HotelGeoBackfill(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        List<Hotel> toSave = new ArrayList<>();
        for (Hotel h : hotelRepository.findByActiveTrue()) {
            boolean changed = false;
            if (h.getLat() == null || h.getLng() == null) {
                CityGeo.Geo g = CityGeo.lookup(h.getCity());
                if (g != null) {
                    // jitter tất định theo id để các marker không chồng nhau
                    long id = h.getId() == null ? 0 : h.getId();
                    double j1 = ((id % 37) - 18) / 1000.0;   // ~±1.8km
                    double j2 = ((id % 29) - 14) / 1000.0;
                    h.setLat(Math.round((g.lat() + j1) * 1_000_000d) / 1_000_000d);
                    h.setLng(Math.round((g.lng() + j2) * 1_000_000d) / 1_000_000d);
                    if (h.getRegion() == null) h.setRegion(g.region());
                    if (h.getProvince() == null || h.getProvince().isBlank()) h.setProvince(h.getCity());
                    changed = true;
                }
            }
            // nếu chưa có thành phần địa chỉ tách mà có address cũ -> để nguyên address, chỉ set province
            if ((h.getProvince() == null || h.getProvince().isBlank()) && h.getCity() != null) {
                h.setProvince(h.getCity());
                changed = true;
            }
            if (changed) toSave.add(h);
        }
        if (!toSave.isEmpty()) {
            hotelRepository.saveAll(toSave);
            log.info("[HotelGeoBackfill] Đã bổ sung toạ độ/khu vực cho {} khách sạn cũ.", toSave.size());
        }
    }
}
