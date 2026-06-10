package com.dididi.booking.invite.repository;

import com.dididi.booking.invite.domain.CompanyInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyInviteRepository extends JpaRepository<CompanyInvite, Long> {
    Optional<CompanyInvite> findByToken(String token);
    List<CompanyInvite> findByCompanyIdOrderByIdDesc(Long companyId);
}
