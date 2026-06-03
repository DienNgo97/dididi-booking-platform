package com.dididi.booking;

import org.springframework.boot.SpringApplication;

public class TestDididiBookingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(DididiBookingPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
