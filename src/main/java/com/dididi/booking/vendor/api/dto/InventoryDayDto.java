package com.dididi.booking.vendor.api.dto;

import com.dididi.booking.hotel.domain.entity.RoomInventory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryDayDto(
        LocalDate date,
        int availableRooms,
        BigDecimal price) {

    public static InventoryDayDto from(RoomInventory inv) {
        return new InventoryDayDto(inv.getDate(), inv.getAvailableRooms(), inv.getPrice());
    }
}
