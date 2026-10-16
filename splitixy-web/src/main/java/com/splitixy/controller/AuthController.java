
package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AuthController {

    private final UserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient;

    public AuthController(
            UserService userService,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.userService = userService;
        this.authorizedClientService = authorizedClientService;
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
            HttpSession session) {

        User user = userService.login(email, password);

        if (user == null) {
            return "redirect:/login?error";
        }

        session.setAttribute("loggedInUser", user);
        return "redirect:/dashboard";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/oauth2/google/register")
    public String googleRegister(HttpSession session) {
        session.setAttribute("oauthIntent", "register");
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/oauth2/google/login")
    public String googleLogin(HttpSession session) {
        session.setAttribute("oauthIntent", "login");
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/oauth2/github/register")
    public String githubRegister(HttpSession session) {
        session.setAttribute("oauthIntent", "register");
        return "redirect:/oauth2/authorization/github";
    }

    @GetMapping("/oauth2/github/login")
    public String githubLogin(HttpSession session) {
        session.setAttribute("oauthIntent", "login");
        return "redirect:/oauth2/authorization/github";
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
        String email;
        String name;

        if ("github".equals(provider)) {
            email = getGitHubEmail(authentication, oauthUser);
            name = oauthUser.getAttribute("name");

            if (name == null || name.isBlank()) {
                name = oauthUser.getAttribute("login");
            }
        } else if ("google".equals(provider)) {
            email = oauthUser.getAttribute("email");
            name = oauthUser.getAttribute("name");
        } else {
            return "redirect:/login?error=provider";
        }

        if (email == null || email.isBlank()) {
            return "redirect:/login?error=email";
        }

        String intent = (String) session.getAttribute("oauthIntent");
        session.removeAttribute("oauthIntent");

        User existingUser = userService.findByEmail(email);

        if ("register".equals(intent)) {
            if (existingUser != null) {
                return "redirect:/login?error=account-exists";
            }

            User user = new User();

            String displayName =
                    (name != null && !name.isBlank())
                            ? name
                            : email.substring(0, email.indexOf("@"));

            user.setFullName(displayName);
            user.setEmail(email);
            user.setPassword(UUID.randomUUID().toString());

            userService.registerUser(user);
            return "redirect:/login?registered=" + provider;
        }

        if (existingUser == null) {
            return "redirect:/register?error=account-not-found";
        }

        session.setAttribute("loggedInUser", existingUser);
        return "redirect:/dashboard";
    }

    private String getGitHubEmail(
            OAuth2AuthenticationToken authentication,
            OAuth2User oauthUser) {

        String profileEmail = oauthUser.getAttribute("email");

        if (profileEmail != null && !profileEmail.isBlank()) {
            return profileEmail;
        }

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
}

