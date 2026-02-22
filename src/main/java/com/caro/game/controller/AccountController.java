package com.caro.game.controller;

import com.caro.game.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    public Object register(@RequestBody RegisterRequest request) {
        AccountService.ServiceResult result = accountService.register(
            new AccountService.RegisterRequest(request.email(), request.displayName(), request.password(), request.avatarPath())
        );
        return toResponse(result);
    }

    @PostMapping("/verify-email")
    public Object verifyEmail(@RequestBody VerifyEmailRequest request) {
        return toResponse(accountService.verifyEmail(request.email(), request.code()));
    }

    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest request) {
        return toResponse(accountService.login(request.email(), request.password()));
    }

    @PostMapping("/logout")
    public Object logout(@RequestBody LogoutRequest request) {
        return toResponse(accountService.logout(request.userId()));
    }

    @PostMapping("/change-password")
    public Object changePassword(@RequestBody ChangePasswordRequest request) {
        return toResponse(accountService.changePassword(request.userId(), request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/send-reset-code")
    public Object sendResetCode(@RequestBody SendResetRequest request) {
        return toResponse(accountService.sendResetCode(request.email()));
    }

    @PostMapping("/verify-reset-code")
    public Object verifyResetCode(@RequestBody VerifyResetRequest request) {
        return toResponse(accountService.verifyResetCode(request.userId(), request.code()));
    }

    @PostMapping("/reset-password")
    public Object resetPassword(@RequestBody ResetPasswordRequest request) {
        return toResponse(accountService.resetPassword(
            request.userId(), request.code(), request.newPassword(), request.confirmPassword()));
    }

    @GetMapping("/ban-notification")
    public Map<String, Object> banNotification(@RequestParam(required = false) String message) {
        return Map.of("message", message == null ? "Your account is banned" : message);
    }

    @GetMapping("/users")
    public Object listUsers(@RequestParam(required = false) String searchTerm,
                            @RequestParam(required = false) String banFilter) {
        return toResponse(accountService.listUsers(searchTerm, banFilter));
    }

    private Object toResponse(AccountService.ServiceResult result) {
        if (result.success()) {
            return Map.of("success", true, "data", result.data());
        }
        return Map.of("success", false, "error", result.error());
    }

    public record RegisterRequest(String email, String displayName, String password, String avatarPath) {
    }

    public record VerifyEmailRequest(String email, String code) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record LogoutRequest(String userId) {
    }

    public record ChangePasswordRequest(String userId, String currentPassword, String newPassword) {
    }

    public record SendResetRequest(String email) {
    }

    public record VerifyResetRequest(String userId, String code) {
    }

    public record ResetPasswordRequest(String userId, String code, String newPassword, String confirmPassword) {
    }
}