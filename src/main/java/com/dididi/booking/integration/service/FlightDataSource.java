package com.dididi.booking.integration.service;

import com.dididi.booking.integration.dto.FlightItem;
import java.util.List;

public interface FlightDataSource {
    List<FlightItem> fetchFlights();
}
