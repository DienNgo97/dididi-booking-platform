package com.dididi.booking.integration.config;

import com.dididi.booking.integration.domain.entity.ExternalDataSource;
import com.dididi.booking.integration.domain.enums.SourceType;
import com.dididi.booking.integration.repository.ExternalDataSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed 2 external data source records (dev) neu chua co.
 */
@Component
@Profile("dev")
public class IntegrationSeeder implements CommandLineRunner {

    private final ExternalDataSourceRepository repo;
    private final String flightEndpoint;
    private final String pmsEndpoint;

    public IntegrationSeeder(ExternalDataSourceRepository repo,
                             @Value("${app.integration.flight-provider.endpoint}") String flightEndpoint,
                             @Value("${app.integration.hotel-pms.endpoint}") String pmsEndpoint) {
        this.repo = repo;
        this.flightEndpoint = flightEndpoint;
        this.pmsEndpoint = pmsEndpoint;
    }

    @Override
    public void run(String... args) {
        if (!repo.existsByCode("FLIGHT_PROVIDER")) {
            ExternalDataSource s = new ExternalDataSource();
            s.setCode("FLIGHT_PROVIDER");
            s.setType(SourceType.FLIGHT);
            s.setEndpoint(flightEndpoint);
            s.setActive(true);
            repo.save(s);
        }
        if (!repo.existsByCode("HOTEL_PMS")) {
            ExternalDataSource s = new ExternalDataSource();
            s.setCode("HOTEL_PMS");
            s.setType(SourceType.HOTEL_PMS);
            s.setEndpoint(pmsEndpoint);
            s.setActive(true);
            repo.save(s);
        }
    }
}
