
package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Controller
public class AuthController {

    private final UserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SecurityContextRepository securityContextRepository;
    private final RestClient restClient;

    public AuthController(
            UserService userService,
            OAuth2AuthorizedClientService authorizedClientService,
            SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.authorizedClientService = authorizedClientService;
        this.securityContextRepository = securityContextRepository;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = userService.login(email, password);

        if (user == null) {
            return "redirect:/login?error";
        }

        User sessionUser = createSessionUser(user);
        request.changeSessionId();
        session.setAttribute("loggedInUser", sessionUser);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                sessionUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return "redirect:/dashboard";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("password") String password) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()
                || password.length() < 8
                || password.isBlank()
                || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "redirect:/register?error=invalid";
        }

        if (userService.findByEmail(normalizedEmail) != null) {
            return "redirect:/register?error=account-exists";
        }

        User user = new User();
        user.setFullName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(password);
        try {
            userService.registerUser(user);
        } catch (DataIntegrityViolationException exception) {
            if (userService.findByEmail(normalizedEmail) == null) {
                throw exception;
            }
            return "redirect:/register?error=account-exists";
        }
        return "redirect:/login";
    }

    @GetMapping("/oauth-success")
    public String oauthSuccess(
            @AuthenticationPrincipal OAuth2User oauthUser,
            OAuth2AuthenticationToken authentication,
            HttpSession session) {

        if (oauthUser == null || authentication == null) {
            return "redirect:/login?error";
        }

        String provider = authentication.getAuthorizedClientRegistrationId();
        if (!"github".equals(provider) && !"google".equals(provider)) {
            return "redirect:/login?error=provider";
        }

        Object rawProviderId = oauthUser.getAttribute("id");
        if (rawProviderId == null) {
            rawProviderId = oauthUser.getAttribute("sub");
        }
        String providerUserId = Objects.toString(rawProviderId, "").trim();
        if (providerUserId.isEmpty()) {
            return "redirect:/login?error=oauth-profile";
        }

        User currentUser = getSessionUser(session);
        User user = userService.findOAuthUser(provider, providerUserId);
        if (user != null) {
            if (currentUser != null && !currentUser.getId().equals(user.getId())) {
                return "redirect:/dashboard?error=oauth-account-linked";
            }
            session.setAttribute("loggedInUser", createSessionUser(user));
            return "redirect:/dashboard";
        }

        if (currentUser != null) {
            try {
                user = userService.linkOAuthAccount(provider, providerUserId, currentUser);
            } catch (UserService.OAuthAccountAlreadyLinkedException exception) {
                return "redirect:/dashboard?error=oauth-account-linked";
            } catch (DataIntegrityViolationException exception) {
                user = userService.findOAuthUser(provider, providerUserId);
                if (user == null || !currentUser.getId().equals(user.getId())) {
                    return "redirect:/dashboard?error=oauth-account-conflict";
                }
            }
            session.setAttribute("loggedInUser", createSessionUser(user));
            return "redirect:/dashboard";
        }

        String email;
        String name = oauthUser.getAttribute("name");
        if ("github".equals(provider)) {
            if (name == null || name.isBlank()) {
                name = oauthUser.getAttribute("login");
            }
            try {
                email = getGitHubEmail(authentication);
            } catch (RestClientException exception) {
                return "redirect:/login?error=github-email-unavailable";
            }
        } else {
            email = oauthUser.getAttribute("email");
            if (!Boolean.TRUE.equals(oauthUser.getAttribute("email_verified"))) {
                email = null;
            }
        }

        if (email == null || email.isBlank()) {
            return "redirect:/login?error=verified-email-required";
        }

        if (userService.findByEmail(email) != null) {
            return "redirect:/login?error=account-link-required";
        }

        try {
            user = userService.createOAuthUser(
                    provider, providerUserId, email, name);
        } catch (UserService.OAuthEmailAlreadyRegisteredException exception) {
            return "redirect:/login?error=account-link-required";
        } catch (DataIntegrityViolationException exception) {
            user = userService.findOAuthUser(provider, providerUserId);
            if (user == null) {
                if (userService.findByEmail(email) != null) {
                    return "redirect:/login?error=account-link-required";
                }
                return "redirect:/login?error=account-conflict";
            }
        }

        session.setAttribute("loggedInUser", createSessionUser(user));
        return "redirect:/dashboard";
    }

    private String getGitHubEmail(
            OAuth2AuthenticationToken authentication) {

        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getName());

        if (client == null || client.getAccessToken() == null) {
            return null;
        }

        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + client.getAccessToken().getTokenValue())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (emails == null) {
            return null;
        }

        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                .map(e -> e.get("email"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }

    private User createSessionUser(User user) {
        User sessionUser = new User();
        sessionUser.setId(user.getId());
        sessionUser.setFullName(user.getFullName());
        sessionUser.setEmail(user.getEmail());
        return sessionUser;
    }
}
