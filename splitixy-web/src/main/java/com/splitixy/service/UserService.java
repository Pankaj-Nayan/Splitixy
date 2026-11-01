package com.splitixy.service;

import com.splitixy.entity.OAuthAccount;
import com.splitixy.entity.User;
import com.splitixy.repository.OAuthAccountRepository;
import com.splitixy.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            OAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(User user) {
        user.setEmail(normalizeEmail(user.getEmail()));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }

    public User login(String email, String password) {
        if (email == null || password == null) {
            return null;
        }

        Optional<User> user = userRepository.findByEmailIgnoreCase(normalizeEmail(email));
        if (user.isEmpty()) {
            return null;
        }

        User existingUser = user.get();
        String storedPassword = existingUser.getPassword();
        if (storedPassword == null) {
            return null;
        }

        if (storedPassword.startsWith("$2")) {
            return passwordEncoder.matches(password, storedPassword) ? existingUser : null;
        }

        if (password.equals(storedPassword)) {
            existingUser.setPassword(passwordEncoder.encode(password));
            userRepository.save(existingUser);
            return existingUser;
        }
        return null;
    }

    @Transactional
    public User createOAuthUser(
            String provider,
            String providerUserId,
            String email,
            String fullName) {

        Optional<OAuthAccount> linkedAccount =
                oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (linkedAccount.isPresent()) {
            return userRepository.findById(linkedAccount.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "OAuth account refers to a missing Splitixy user"));
        }

        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new OAuthEmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setFullName(
                fullName == null || fullName.isBlank()
                        ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                        : fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user = userRepository.saveAndFlush(user);
        oauthAccountRepository.saveAndFlush(
                new OAuthAccount(provider, providerUserId, user.getId()));
        return user;
    }

    @Transactional
    public User linkOAuthAccount(
            String provider,
            String providerUserId,
            User accountOwner) {
        Optional<OAuthAccount> linkedAccount =
                oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (linkedAccount.isPresent()) {
            if (linkedAccount.get().getUserId().equals(accountOwner.getId())) {
                return accountOwner;
            }
            throw new OAuthAccountAlreadyLinkedException();
        }

        oauthAccountRepository.saveAndFlush(
                new OAuthAccount(provider, providerUserId, accountOwner.getId()));
        return accountOwner;
    }

    public User findOAuthUser(String provider, String providerUserId) {
        return oauthAccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .flatMap(account -> userRepository.findById(account.getUserId()))
                .orElse(null);
    }

    public User getUserById(Integer id) {
        return userRepository.findById(id).orElse(null);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User findByEmail(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email)).orElse(null);
    }

    public User updateFullName(Integer userId, String fullName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        user.setFullName(fullName.trim());
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static class OAuthEmailAlreadyRegisteredException extends RuntimeException {
    }

    public static class OAuthAccountAlreadyLinkedException extends RuntimeException {
    }
}
