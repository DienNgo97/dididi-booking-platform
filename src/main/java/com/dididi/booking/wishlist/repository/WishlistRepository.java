package com.dididi.booking.wishlist.repository;

import com.dididi.booking.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByUserIdOrderByIdDesc(Long userId);
    Optional<Wishlist> findByUserIdAndHotelId(Long userId, Long hotelId);
    boolean existsByUserIdAndHotelId(Long userId, Long hotelId);
}
