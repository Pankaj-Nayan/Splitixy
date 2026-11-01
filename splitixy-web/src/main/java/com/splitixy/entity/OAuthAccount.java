package com.splitixy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "oauth_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oauth_provider_user",
                columnNames = {"provider", "provider_user_id"}))
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    public OAuthAccount() {
    }

    public OAuthAccount(String provider, String providerUserId, Integer userId) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.userId = userId;
    }

    public Integer getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public Integer getUserId() {
        return userId;
    }
}
