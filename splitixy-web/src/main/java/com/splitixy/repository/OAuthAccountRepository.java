package com.splitixy.repository;

import com.splitixy.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Integer> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(
            String provider,
            String providerUserId);
}
