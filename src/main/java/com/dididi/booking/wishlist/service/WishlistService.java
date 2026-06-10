package com.dididi.booking.wishlist.service;

import com.dididi.booking.common.exception.BusinessException;
import com.dididi.booking.hotel.domain.entity.Hotel;
import com.dididi.booking.hotel.repository.HotelRepository;
import com.dididi.booking.wishlist.domain.Wishlist;
import com.dididi.booking.wishlist.repository.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Quan ly danh sach khach san yeu thich. */
@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final HotelRepository hotelRepository;

    public WishlistService(WishlistRepository wishlistRepository, HotelRepository hotelRepository) {
        this.wishlistRepository = wishlistRepository;
        this.hotelRepository = hotelRepository;
    }

    /** Bat/tat yeu thich. Tra ve true neu vua THEM, false neu vua BO. */
    @Transactional
    public boolean toggle(Long userId, Long hotelId) {
        var existing = wishlistRepository.findByUserIdAndHotelId(userId, hotelId);
        if (existing.isPresent()) {
            wishlistRepository.delete(existing.get());
            return false;
        }
        if (!hotelRepository.existsById(hotelId)) {
            throw new BusinessException("HOTEL_NOT_FOUND", "Không tìm thấy khách sạn", HttpStatus.NOT_FOUND);
        }
        Wishlist w = new Wishlist();
        w.setUserId(userId);
        w.setHotelId(hotelId);
        wishlistRepository.save(w);
        return true;
    }

    public boolean isWishlisted(Long userId, Long hotelId) {
        return wishlistRepository.existsByUserIdAndHotelId(userId, hotelId);
    }

    /** Danh sach hotelId yeu thich (de danh dau tim tren trang list). */
    public List<Long> wishlistedHotelIds(Long userId) {
        List<Long> ids = new ArrayList<>();
        for (Wishlist w : wishlistRepository.findByUserIdOrderByIdDesc(userId)) ids.add(w.getHotelId());
        return ids;
    }

    /** Cac khach san yeu thich (bo qua KS da bi xoa), giu thu tu moi nhat truoc. */
    public List<Hotel> listHotels(Long userId) {
        List<Hotel> out = new ArrayList<>();
        for (Wishlist w : wishlistRepository.findByUserIdOrderByIdDesc(userId)) {
            hotelRepository.findById(w.getHotelId()).ifPresent(out::add);
        }
        return out;
    }
}
