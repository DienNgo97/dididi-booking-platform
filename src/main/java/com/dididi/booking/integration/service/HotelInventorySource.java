package com.dididi.booking.integration.service;

import com.dididi.booking.integration.dto.HotelItem;
import java.util.List;

public interface HotelInventorySource {
    List<HotelItem> fetchHotels();
}
