package com.dididi.booking.corporate.repository;

import com.dididi.booking.corporate.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCode(String code);
    Optional<Company> findByCode(String code);
    List<Company> findByActiveTrueOrderByName();
    List<Company> findAllByOrderByName();
}
