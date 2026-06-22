package com.dididi.booking.identity.repository;

import com.dididi.booking.identity.domain.entity.UserToken;
import com.dididi.booking.identity.domain.enums.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenAndPurpose(String token, TokenPurpose purpose);
}
