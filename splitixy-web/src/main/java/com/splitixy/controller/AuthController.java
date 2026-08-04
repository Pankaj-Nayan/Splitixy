
package com.splitixy.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.splitixy.entity.User;
import com.splitixy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/login")
    public String loginUser(@RequestParam String email,
                            @RequestParam String password) {

        User user = userService.login(email, password);

        if (user != null) {
            return "redirect:/dashboard";
        }

        return "redirect:/login";
    }
}