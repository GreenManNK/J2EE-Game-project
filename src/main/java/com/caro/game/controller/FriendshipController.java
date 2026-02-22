package com.caro.game.controller;

import com.caro.game.entity.AchievementNotification;
import com.caro.game.entity.Friendship;
import com.caro.game.entity.SystemNotification;
import com.caro.game.entity.UserAccount;
import com.caro.game.repository.AchievementNotificationRepository;
import com.caro.game.repository.SystemNotificationRepository;
import com.caro.game.repository.UserAccountRepository;
import com.caro.game.service.FriendshipService;
import com.caro.game.service.ProfileStatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/friendship")
public class FriendshipController {
    private final FriendshipService friendshipService;
    private final UserAccountRepository userAccountRepository;
    private final AchievementNotificationRepository achievementNotificationRepository;
    private final SystemNotificationRepository systemNotificationRepository;
    private final ProfileStatsService profileStatsService;

    public FriendshipController(FriendshipService friendshipService,
                                UserAccountRepository userAccountRepository,
                                AchievementNotificationRepository achievementNotificationRepository,
                                SystemNotificationRepository systemNotificationRepository,
                                ProfileStatsService profileStatsService) {
        this.friendshipService = friendshipService;
        this.userAccountRepository = userAccountRepository;
        this.achievementNotificationRepository = achievementNotificationRepository;
        this.systemNotificationRepository = systemNotificationRepository;
        this.profileStatsService = profileStatsService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String currentUserId, Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        model.addAllAttributes(buildIndex(resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> index(@RequestParam(required = false) String currentUserId) {
        return buildIndex(resolveCurrentUserId(currentUserId));
    }

    @ResponseBody
    @PostMapping("/send-request")
    public Map<String, Object> sendRequest(@RequestBody SendByEmailRequest request) {
        UserAccount target = userAccountRepository.findByEmail(request.email()).orElse(null);
        if (target == null) {
            return Map.of("success", false, "error", "Target email not found");
        }

        boolean ok = friendshipService.sendRequest(request.requesterId(), target.getId());
        return Map.of("success", ok);
    }

    @ResponseBody
    @PostMapping("/send-request-by-id")
    public Map<String, Object> sendRequestById(@RequestBody SendByIdRequest request) {
        boolean ok = friendshipService.sendRequest(request.requesterId(), request.addresseeId());
        return Map.of("success", ok);
    }

    @ResponseBody
    @PostMapping("/accept")
    public Map<String, Object> accept(@RequestBody FriendshipActionRequest request) {
        return Map.of("success", friendshipService.acceptRequest(request.friendshipId()));
    }

    @ResponseBody
    @PostMapping("/decline")
    public Map<String, Object> decline(@RequestBody FriendshipActionRequest request) {
        return Map.of("success", friendshipService.declineRequest(request.friendshipId()));
    }

    @ResponseBody
    @PostMapping("/remove")
    public Map<String, Object> remove(@RequestBody RemoveFriendRequest request) {
        return Map.of("success", friendshipService.removeFriendship(request.userId(), request.friendId()));
    }

    @GetMapping("/search")
    public String searchPage(@RequestParam String query,
                             @RequestParam(required = false) String currentUserId,
                             Model model) {
        model.addAllAttributes(search(query));
        model.addAttribute("currentUserId", resolveCurrentUserId(currentUserId));
        return "friendship/search";
    }

    @ResponseBody
    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return Map.of("query", "", "exactMatches", List.of(), "similarMatches", List.of());
        }

        String normalized = query.toLowerCase();
        List<UserAccount> allUsers = userAccountRepository.findAll();

        List<UserAccount> exactMatches = allUsers.stream()
            .filter(u -> (u.getDisplayName() != null && u.getDisplayName().toLowerCase().equals(normalized))
                || (u.getEmail() != null && u.getEmail().toLowerCase().equals(normalized)))
            .toList();

        List<UserAccount> similar;
        if (!exactMatches.isEmpty()) {
            List<String> exactIds = exactMatches.stream().map(UserAccount::getId).toList();
            similar = allUsers.stream()
                .filter(u -> ((u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(normalized))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(normalized)))
                    && !exactIds.contains(u.getId()))
                .toList();
        } else {
            similar = allUsers.stream()
                .filter(u -> {
                    String name = u.getDisplayName() == null ? "" : u.getDisplayName().toLowerCase();
                    String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                    int match = Math.max(longestCommonSubstringLength(name, normalized), longestCommonSubstringLength(email, normalized));
                    return match >= 5;
                })
                .toList();
        }

        return Map.of("query", query, "exactMatches", exactMatches, "similarMatches", similar);
    }

    @GetMapping("/user-detail/{id}")
    public String userDetailPage(@PathVariable String id,
                                 @RequestParam(required = false) String currentUserId,
                                 Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        model.addAllAttributes(userDetail(id, resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/user-detail";
    }

    @ResponseBody
    @GetMapping("/api/user-detail/{id}")
    public Map<String, Object> userDetail(@PathVariable String id,
                                          @RequestParam(required = false) String currentUserId) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        Map<String, Object> profile = new HashMap<>(profileStatsService.buildProfileStats(id, resolvedUserId));
        profile.put("isFriend", friendshipService.areFriends(resolvedUserId, id));
        profile.put("hasPending", friendshipService.hasPendingRequest(resolvedUserId, id)
            || friendshipService.hasPendingRequest(id, resolvedUserId));
        return profile;
    }

    @ResponseBody
    @GetMapping("/friend-list")
    public List<UserAccount> friendList(@RequestParam(required = false) String currentUserId) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            return List.of();
        }
        return friendshipService.getFriends(resolvedUserId);
    }

    @GetMapping("/notifications")
    public String notificationsPage(@RequestParam(required = false) String currentUserId, Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        model.addAllAttributes(notifications(resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/notifications";
    }

    @ResponseBody
    @GetMapping("/api/notifications")
    public Map<String, Object> notifications(@RequestParam(required = false) String currentUserId) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            return Map.of(
                "friendRequests", List.of(),
                "achievementNotifications", List.of(),
                "systemNotifications", systemNotificationRepository.findTop5ByOrderByCreatedAtDesc()
            );
        }
        UserAccount user = userAccountRepository.findById(resolvedUserId).orElseThrow();

        List<Friendship> pendingRequests = friendshipService.getPendingRequests(resolvedUserId);
        List<AchievementNotification> unread = achievementNotificationRepository.findUnreadByUserId(resolvedUserId);
        for (AchievementNotification n : unread) {
            n.setRead(true);
        }
        achievementNotificationRepository.saveAll(unread);

        List<SystemNotification> systemNotis = systemNotificationRepository.findTop5ByOrderByCreatedAtDesc();
        user.setLastSystemNotificationSeenAt(LocalDateTime.now());
        userAccountRepository.save(user);

        List<AchievementNotification> allAchievementNotis = achievementNotificationRepository.findByUserIdOrderByCreatedAtDesc(resolvedUserId);

        return Map.of(
            "friendRequests", pendingRequests,
            "achievementNotifications", allAchievementNotis,
            "systemNotifications", systemNotis
        );
    }

    private Map<String, Object> buildIndex(String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            return Map.of(
                "friends", List.of(),
                "pendingRequests", List.of(),
                "sentRequests", List.of()
            );
        }
        return Map.of(
            "friends", friendshipService.getFriends(currentUserId),
            "pendingRequests", friendshipService.getPendingRequests(currentUserId),
            "sentRequests", friendshipService.getSentRequests(currentUserId)
        );
    }

    private String resolveCurrentUserId(String currentUserId) {
        if (currentUserId != null && !currentUserId.isBlank()) {
            return currentUserId;
        }
        return userAccountRepository.findAll().stream()
            .map(UserAccount::getId)
            .findFirst()
            .orElse("");
    }

    private int longestCommonSubstringLength(String source, String target) {
        int[][] table = new int[source.length() + 1][target.length() + 1];
        int max = 0;

        for (int i = 1; i <= source.length(); i++) {
            for (int j = 1; j <= target.length(); j++) {
                if (source.charAt(i - 1) == target.charAt(j - 1)) {
                    table[i][j] = table[i - 1][j - 1] + 1;
                    max = Math.max(max, table[i][j]);
                }
            }
        }

        return max;
    }

    public record SendByEmailRequest(String requesterId, String email) {
    }

    public record SendByIdRequest(String requesterId, String addresseeId) {
    }

    public record FriendshipActionRequest(Long friendshipId) {
    }

    public record RemoveFriendRequest(String userId, String friendId) {
    }
}
