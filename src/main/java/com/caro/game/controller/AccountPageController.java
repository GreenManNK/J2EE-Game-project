package com.caro.game.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/account")
public class AccountPageController {

    @GetMapping("/login-page")
    public String loginPage() {
        return "account/login";
    }

    @GetMapping("/register-page")
    public String registerPage() {
        return "account/register";
    }

    @GetMapping("/verify-email-page")
    public String verifyEmailPage() {
        return "account/verify-email";
    }

    @GetMapping("/reset-page")
    public String resetPage() {
        return "account/reset-password";
    }

    @GetMapping("/ban-notification-page")
    public String banPage(@RequestParam(required = false) String message,
                          org.springframework.ui.Model model) {
        model.addAttribute("message", message == null ? "Tai khoan cua ban da bi khoa tam thoi." : message);
        return "account/ban-notification";
    }
}