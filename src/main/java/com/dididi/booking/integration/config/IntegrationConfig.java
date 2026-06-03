package com.dididi.booking.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bat @Scheduled cho integration module (khong can sua DididiBookingPlatformApplication).
 */
@Configuration
@EnableScheduling
public class IntegrationConfig {
}
