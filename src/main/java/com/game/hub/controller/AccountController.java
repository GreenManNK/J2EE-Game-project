package com.game.hub.controller;

import com.game.hub.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    public Object verifyEmail(@RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
        AccountService.ServiceResult result = accountService.verifyEmail(request.email(), request.code());
        if (result.success() && result.data() instanceof Map<?, ?> data) {
            HttpSession session = httpRequest.getSession(true);
            Object userId = data.get("userId");
            Object role = data.get("role");
            if (userId instanceof String uid && !uid.isBlank()) {
                session.setAttribute("AUTH_USER_ID", uid);
            }
            if (role instanceof String r && !r.isBlank()) {
                session.setAttribute("AUTH_ROLE", r);
            }
        }
        return toResponse(result);
    }

    @PostMapping("/resend-verification-code")
    public Object resendVerificationCode(@RequestBody SendVerificationCodeRequest request) {
        return toResponse(accountService.resendVerificationCode(request == null ? null : request.email()));
    }

    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AccountService.ServiceResult result = accountService.login(request.email(), request.password());
        if (result.success() && result.data() instanceof Map<?, ?> data) {
            HttpSession session = httpRequest.getSession(true);
            Object userId = data.get("userId");
            Object role = data.get("role");
            if (userId instanceof String uid && !uid.isBlank()) {
                session.setAttribute("AUTH_USER_ID", uid);
            }
            if (role instanceof String r && !r.isBlank()) {
                session.setAttribute("AUTH_ROLE", r);
            }
        }
        return toResponse(result);
    }

    @PostMapping("/logout")
    public Object logout(@RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (session != null) {
            session.removeAttribute("AUTH_USER_ID");
            session.removeAttribute("AUTH_ROLE");
        }
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", true, "data", Map.of("loggedOut", true));
        }
        return toResponse(accountService.logout(sessionUserId));
    }

    @PostMapping("/change-password")
    public Object changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            return Map.of("success", false, "error", "UserId is required");
        }
        if (!sessionUserId.equals(request.userId())) {
            return Map.of("success", false, "error", "User mismatch");
        }
        return toResponse(accountService.changePassword(request.userId(), request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/update-profile")
    public Object updateProfile(@RequestBody(required = false) UpdateProfileRequest request,
                                HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String requestedUserId = request == null ? null : request.userId();
        if (requestedUserId != null && !requestedUserId.isBlank() && !sessionUserId.equals(requestedUserId)) {
            return Map.of("success", false, "error", "User mismatch");
        }

        return toResponse(accountService.updateProfile(
            sessionUserId,
            request == null ? null : request.displayName(),
            request == null ? null : request.email(),
            request == null ? null : request.avatarPath()
        ));
    }

    @GetMapping("/preferences")
    public Object getPreferences(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        return toResponse(accountService.getPreferences(sessionUserId));
    }

    @PostMapping("/preferences")
    public Object updatePreferences(@RequestBody(required = false) UpdatePreferencesRequest request,
                                    HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        if (request == null) {
            return Map.of("success", false, "error", "Invalid request");
        }

        return toResponse(accountService.updatePreferences(
            sessionUserId,
            new AccountService.PreferencesRequest(
                request.themeMode(),
                request.language(),
                request.sidebarDesktopVisibleByDefault(),
                request.sidebarMobileAutoClose(),
                request.homeMusicEnabled(),
                request.toastNotificationsEnabled(),
                request.showOfflineFriendsInSidebar(),
                request.autoRefreshFriendList(),
                request.friendListRefreshMs()
            )
        ));
    }

    @GetMapping("/game-stats")
    public Object getGameStats(@RequestParam(required = false) String gameCode,
                               HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        return toResponse(accountService.getGameStats(sessionUserId, gameCode));
    }

    @PostMapping("/game-stats")
    public Object updateGameStats(@RequestBody(required = false) UpdateGameStatsRequest request,
                                  HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        if (request == null) {
            return Map.of("success", false, "error", "Invalid request");
        }
        return toResponse(accountService.updateGameStats(
            sessionUserId,
            request.gameCode(),
            request.stats(),
            request.merge() == null || request.merge()
        ));
    }

    @PostMapping("/activate-admin")
    public Object activateAdmin(@RequestBody(required = false) ActivateAdminRequest request,
                                HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        String sessionUserId = session == null ? null : asString(session.getAttribute("AUTH_USER_ID"));
        if (sessionUserId == null || sessionUserId.isBlank()) {
            return Map.of("success", false, "error", "Login required");
        }
        String requestedUserId = request == null ? null : request.userId();
        if (requestedUserId != null && !requestedUserId.isBlank() && !sessionUserId.equals(requestedUserId)) {
            return Map.of("success", false, "error", "User mismatch");
        }

        AccountService.ServiceResult result =
            accountService.activateAdminRole(sessionUserId, request == null ? null : request.activationCode());
        if (result.success() && session != null) {
            session.setAttribute("AUTH_USER_ID", sessionUserId);
            session.setAttribute("AUTH_ROLE", "Admin");
        }
        return toResponse(result);
    }

    @PostMapping("/send-reset-code")
    public Object sendResetCode(@RequestBody SendResetRequest request) {
        return toResponse(accountService.sendResetCode(request.email()));
    }

    @PostMapping("/verify-reset-code")
    public Object verifyResetCode(@RequestBody VerifyResetRequest request) {
        if (request == null) {
            return Map.of("success", false, "error", "Email/UserId and code are required");
        }
        if (request.userId() != null && !request.userId().isBlank()) {
            return toResponse(accountService.verifyResetCode(request.userId(), request.code()));
        }
        return toResponse(accountService.verifyResetCodeByEmail(request.email(), request.code()));
    }

    @PostMapping("/reset-password")
    public Object resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request == null) {
            return Map.of("success", false, "error", "Invalid request");
        }
        if (request.userId() != null && !request.userId().isBlank()) {
            return toResponse(accountService.resetPassword(
                request.userId(), request.code(), request.newPassword(), request.confirmPassword()));
        }
        return toResponse(accountService.resetPasswordByEmail(
            request.email(), request.code(), request.newPassword(), request.confirmPassword()));
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record RegisterRequest(String email, String displayName, String password, String avatarPath) {
    }

    public record VerifyEmailRequest(String email, String code) {
    }

    public record SendVerificationCodeRequest(String email) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record LogoutRequest(String userId) {
    }

    public record ChangePasswordRequest(String userId, String currentPassword, String newPassword) {
    }

    public record UpdateProfileRequest(String userId, String displayName, String email, String avatarPath) {
    }

    public record UpdatePreferencesRequest(String themeMode,
                                           String language,
                                           Boolean sidebarDesktopVisibleByDefault,
                                           Boolean sidebarMobileAutoClose,
                                           Boolean homeMusicEnabled,
                                           Boolean toastNotificationsEnabled,
                                           Boolean showOfflineFriendsInSidebar,
                                           Boolean autoRefreshFriendList,
                                           Integer friendListRefreshMs) {
    }

    public record ActivateAdminRequest(String userId, String activationCode) {
    }

    public record SendResetRequest(String email) {
    }

    public record VerifyResetRequest(String userId, String email, String code) {
    }

    public record ResetPasswordRequest(String userId, String email, String code, String newPassword, String confirmPassword) {
    }

    public record UpdateGameStatsRequest(String gameCode, Object stats, Boolean merge) {
    }
}
