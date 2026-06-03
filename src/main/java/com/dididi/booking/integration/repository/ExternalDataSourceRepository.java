package com.dididi.booking.integration.repository;

import com.dididi.booking.integration.domain.entity.ExternalDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalDataSourceRepository extends JpaRepository<ExternalDataSource, Long> {
    Optional<ExternalDataSource> findByCode(String code);
    boolean existsByCode(String code);
}
