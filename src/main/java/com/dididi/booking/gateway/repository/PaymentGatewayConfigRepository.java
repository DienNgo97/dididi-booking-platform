package com.dididi.booking.gateway.repository;

import com.dididi.booking.gateway.domain.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, Long> {
    Optional<PaymentGatewayConfig> findByProvider(String provider);
}
