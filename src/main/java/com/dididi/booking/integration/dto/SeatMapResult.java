package com.dididi.booking.integration.dto;

import java.util.List;

/** So do ghe day du cua 1 chuyen, pull tu flight-provider. */
public record SeatMapResult(
        Long flightId,
        String flightNumber,
        String currency,
        int rows,
        String[] cols,
        int businessRows,
        List<SeatItem> seats) {
}
