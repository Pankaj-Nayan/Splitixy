package com.splitixy.service;

import com.splitixy.entity.OAuthAccount;
import com.splitixy.entity.User;
import com.splitixy.repository.OAuthAccountRepository;
import com.splitixy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthAccountRepository oauthAccountRepository;

    private UserService userService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(
                userRepository,
                oauthAccountRepository,
                passwordEncoder);
    }

    @Test
    void registerUserStoresEncodedPassword() {
        User user = new User();
        user.setEmail("person@example.com");
        user.setPassword("strong-password");

        userService.registerUser(user);

        assertNotEquals("strong-password", user.getPassword());
        assertTrue(passwordEncoder.matches("strong-password", user.getPassword()));
        assertEquals("person@example.com", user.getEmail());
        verify(userRepository).save(user);
    }

    @Test
    void loginAcceptsEncodedPasswordAndRejectsInvalidPassword() {
        User user = new User();
        user.setEmail("person@example.com");
        user.setPassword(passwordEncoder.encode("strong-password"));
        when(userRepository.findByEmailIgnoreCase("person@example.com"))
                .thenReturn(Optional.of(user));

        assertSame(user, userService.login("PERSON@example.com", "strong-password"));
        assertNull(userService.login("person@example.com", "wrong-password"));
    }

    @Test
    void oauthRegistrationDoesNotLinkByMatchingEmail() {
        User user = new User();
        user.setId(42);
        user.setEmail("person@example.com");
        when(oauthAccountRepository.findByProviderAndProviderUserId("github", "1234"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("person@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(
                UserService.OAuthEmailAlreadyRegisteredException.class,
                () -> userService.createOAuthUser(
                        "github", "1234", "PERSON@example.com", "Person"));
        verify(oauthAccountRepository, never()).saveAndFlush(any(OAuthAccount.class));
    }

    @Test
    void explicitlyLinksOAuthAccountToAuthenticatedUser() {
        User user = new User();
        user.setId(42);
        when(oauthAccountRepository.findByProviderAndProviderUserId("github", "1234"))
                .thenReturn(Optional.empty());

        assertSame(user, userService.linkOAuthAccount("github", "1234", user));
        verify(oauthAccountRepository).saveAndFlush(any(OAuthAccount.class));
    }
}
