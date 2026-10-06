package com.pickleball.common.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestAccessRepository extends JpaRepository<GuestAccessToken, Long> {
    Optional<GuestAccessToken> findByTokenHash(String tokenHash);
}
