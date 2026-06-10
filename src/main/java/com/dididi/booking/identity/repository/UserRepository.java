package com.dididi.booking.identity.repository;

import com.dididi.booking.identity.domain.entity.User;
import com.dididi.booking.identity.domain.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // ---- Admin (Phase 4b) ----
    Page<User> findByRole(Role role, Pageable pageable);

    // ---- Corporate B2B (Dot 3) ----
    List<User> findByCompanyIdOrderByEmail(Long companyId);
}
