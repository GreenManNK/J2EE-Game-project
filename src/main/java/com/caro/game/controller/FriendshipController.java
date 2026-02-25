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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import java.util.ArrayList;
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
    public String page(@RequestParam(required = false) String currentUserId,
                       HttpServletRequest request,
                       Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId, request);
        model.addAllAttributes(buildIndex(resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/index";
    }

    @ResponseBody
    @GetMapping("/api")
    public Map<String, Object> index(@RequestParam(required = false) String currentUserId,
                                     HttpServletRequest request) {
        return buildIndex(resolveCurrentUserId(currentUserId, request));
    }

    @ResponseBody
    @PostMapping("/send-request")
    public Map<String, Object> sendRequest(@RequestBody SendByEmailRequest requestBody, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.requesterId() == null || requestBody.requesterId().isBlank()) {
            return Map.of("success", false, "error", "Requester is required");
        }
        if (!sessionUserId.equals(requestBody.requesterId())) {
            return Map.of("success", false, "error", "Requester mismatch");
        }
        if (userAccountRepository.findById(sessionUserId).isEmpty()) {
            return Map.of("success", false, "error", "Requester not found");
        }
        UserAccount target = userAccountRepository.findByEmail(requestBody.email()).orElse(null);
        if (target == null) {
            return Map.of("success", false, "error", "Target email not found");
        }

        boolean ok = friendshipService.sendRequest(sessionUserId, target.getId());
        return Map.of("success", ok);
    }

    @ResponseBody
    @PostMapping("/send-request-by-id")
    public Map<String, Object> sendRequestById(@RequestBody SendByIdRequest requestBody, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.requesterId() == null || requestBody.addresseeId() == null
            || requestBody.requesterId().isBlank() || requestBody.addresseeId().isBlank()) {
            return Map.of("success", false, "error", "Requester/Addressee is required");
        }
        if (!sessionUserId.equals(requestBody.requesterId())) {
            return Map.of("success", false, "error", "Requester mismatch");
        }
        if (userAccountRepository.findById(sessionUserId).isEmpty()
            || userAccountRepository.findById(requestBody.addresseeId()).isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }
        boolean ok = friendshipService.sendRequest(sessionUserId, requestBody.addresseeId());
        return Map.of("success", ok);
    }

    @ResponseBody
    @PostMapping("/accept")
    public Map<String, Object> accept(@RequestBody FriendshipActionRequest requestBody, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.friendshipId() == null) {
            return Map.of("success", false, "error", "Friendship id is required");
        }
        String friendUserId = friendshipService.getPendingRequests(sessionUserId).stream()
            .filter(link -> link != null && requestBody.friendshipId().equals(link.getId()))
            .map(Friendship::getRequesterId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst()
            .orElse(null);

        boolean ok = friendshipService.acceptRequest(requestBody.friendshipId(), sessionUserId);
        if (!ok) {
            return Map.of("success", false);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (friendUserId != null) {
            UserAccount acceptedFriend = userAccountRepository.findById(friendUserId).orElse(null);
            if (acceptedFriend != null) {
                response.put("acceptedFriend", toFriendView(acceptedFriend));
            }
        }
        return response;
    }

    @ResponseBody
    @PostMapping("/decline")
    public Map<String, Object> decline(@RequestBody FriendshipActionRequest requestBody, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.friendshipId() == null) {
            return Map.of("success", false, "error", "Friendship id is required");
        }
        return Map.of("success", friendshipService.declineRequest(requestBody.friendshipId(), sessionUserId));
    }

    @ResponseBody
    @PostMapping("/remove")
    public Map<String, Object> remove(@RequestBody RemoveFriendRequest requestBody, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId == null) {
            return Map.of("success", false, "error", "Login required");
        }
        if (requestBody == null || requestBody.friendId() == null || requestBody.friendId().isBlank()) {
            return Map.of("success", false, "error", "Friend id is required");
        }
        if (requestBody.userId() != null && !requestBody.userId().isBlank() && !sessionUserId.equals(requestBody.userId())) {
            return Map.of("success", false, "error", "User mismatch");
        }
        return Map.of("success", friendshipService.removeFriendship(sessionUserId, requestBody.friendId()));
    }

    @GetMapping("/search")
    public String searchPage(@RequestParam String query,
                             @RequestParam(required = false) String currentUserId,
                             HttpServletRequest request,
                             Model model) {
        model.addAllAttributes(search(query));
        model.addAttribute("currentUserId", resolveCurrentUserId(currentUserId, request));
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
                                 HttpServletRequest request,
                                 Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId, request);
        if (id == null || id.isBlank() || userAccountRepository.findById(id).isEmpty()) {
            return "redirect:/friendship?currentUserId=" + resolvedUserId;
        }
        model.addAllAttributes(userDetail(id, resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/user-detail";
    }

    @ResponseBody
    @GetMapping("/api/user-detail/{id}")
    public Map<String, Object> userDetail(@PathVariable String id,
                                          @RequestParam(required = false) String currentUserId,
                                          HttpServletRequest request) {
        return userDetail(id, resolveCurrentUserId(currentUserId, request));
    }

    public Map<String, Object> userDetail(String id,
                                          String currentUserId) {
        if (id == null || id.isBlank() || userAccountRepository.findById(id).isEmpty()) {
            return Map.of("success", false, "error", "User not found");
        }
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        Map<String, Object> profile = new HashMap<>(profileStatsService.buildProfileStats(id, resolvedUserId));
        profile.put("success", true);
        profile.put("isFriend", friendshipService.areFriends(resolvedUserId, id));
        profile.put("hasPending", friendshipService.hasPendingRequest(resolvedUserId, id)
            || friendshipService.hasPendingRequest(id, resolvedUserId));
        return profile;
    }

    @ResponseBody
    @GetMapping("/friend-list")
    public List<UserAccount> friendList(@RequestParam(required = false) String currentUserId,
                                        HttpServletRequest request) {
        String resolvedUserId = resolveCurrentUserId(currentUserId, request);
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            return List.of();
        }
        return friendshipService.getFriends(resolvedUserId);
    }

    @GetMapping("/notifications")
    public String notificationsPage(@RequestParam(required = false) String currentUserId,
                                    HttpServletRequest request,
                                    Model model) {
        String resolvedUserId = resolveCurrentUserId(currentUserId, request);
        model.addAllAttributes(notifications(resolvedUserId));
        model.addAttribute("currentUserId", resolvedUserId);
        return "friendship/notifications";
    }

    @ResponseBody
    @GetMapping("/api/notifications")
    public Map<String, Object> notifications(@RequestParam(required = false) String currentUserId,
                                             HttpServletRequest request) {
        return notifications(resolveCurrentUserId(currentUserId, request));
    }

    public Map<String, Object> notifications(@RequestParam(required = false) String currentUserId) {
        String resolvedUserId = resolveCurrentUserId(currentUserId);
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            return Map.of(
                "friendRequests", List.of(),
                "friendRequestViews", List.of(),
                "achievementNotifications", List.of(),
                "systemNotifications", systemNotificationRepository.findTop5ByOrderByCreatedAtDesc()
            );
        }
        UserAccount user = userAccountRepository.findById(resolvedUserId).orElse(null);
        if (user == null) {
            return Map.of(
                "friendRequests", List.of(),
                "friendRequestViews", List.of(),
                "achievementNotifications", List.of(),
                "systemNotifications", systemNotificationRepository.findTop5ByOrderByCreatedAtDesc()
            );
        }

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
        List<FriendRequestView> friendRequestViews = buildFriendRequestViews(pendingRequests);

        return Map.of(
            "friendRequests", pendingRequests,
            "friendRequestViews", friendRequestViews,
            "achievementNotifications", allAchievementNotis,
            "systemNotifications", systemNotis
        );
    }

    private List<FriendRequestView> buildFriendRequestViews(List<Friendship> pendingRequests) {
        if (pendingRequests == null || pendingRequests.isEmpty()) {
            return List.of();
        }

        List<String> requesterIds = pendingRequests.stream()
            .map(Friendship::getRequesterId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        Map<String, UserAccount> requesterById = new HashMap<>();
        for (UserAccount account : userAccountRepository.findAllById(requesterIds)) {
            requesterById.put(account.getId(), account);
        }

        List<FriendRequestView> result = new ArrayList<>();
        for (Friendship friendship : pendingRequests) {
            if (friendship == null) {
                continue;
            }
            UserAccount requester = requesterById.get(friendship.getRequesterId());
            String requesterName = requester == null
                ? friendship.getRequesterId()
                : (requester.getDisplayName() == null || requester.getDisplayName().isBlank()
                    ? requester.getEmail()
                    : requester.getDisplayName());
            String requesterEmail = requester == null ? "" : (requester.getEmail() == null ? "" : requester.getEmail());
            String requesterAvatarPath = requester == null || requester.getAvatarPath() == null || requester.getAvatarPath().isBlank()
                ? "/uploads/avatars/default-avatar.jpg"
                : requester.getAvatarPath();
            result.add(new FriendRequestView(
                friendship.getId(),
                friendship.getRequesterId(),
                requesterName,
                requesterEmail,
                requesterAvatarPath,
                friendship.getAddresseeId()
            ));
        }
        return result;
    }

    private Map<String, Object> buildIndex(String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            return Map.of(
                "friends", List.of(),
                "friendViews", List.of(),
                "pendingRequests", List.of(),
                "pendingRequestViews", List.of(),
                "sentRequests", List.of(),
                "sentRequestViews", List.of()
            );
        }
        List<UserAccount> friends = friendshipService.getFriends(currentUserId);
        List<Friendship> pendingRequests = friendshipService.getPendingRequests(currentUserId);
        List<Friendship> sentRequests = friendshipService.getSentRequests(currentUserId);
        return Map.of(
            "friends", friends,
            "friendViews", buildFriendViews(friends),
            "pendingRequests", pendingRequests,
            "pendingRequestViews", buildFriendRequestViews(pendingRequests),
            "sentRequests", sentRequests,
            "sentRequestViews", buildSentRequestViews(sentRequests)
        );
    }

    private List<FriendView> buildFriendViews(List<UserAccount> friends) {
        if (friends == null || friends.isEmpty()) {
            return List.of();
        }
        List<FriendView> result = new ArrayList<>();
        for (UserAccount account : friends) {
            if (account == null || account.getId() == null || account.getId().isBlank()) {
                continue;
            }
            result.add(toFriendView(account));
        }
        return result;
    }

    private List<SentRequestView> buildSentRequestViews(List<Friendship> sentRequests) {
        if (sentRequests == null || sentRequests.isEmpty()) {
            return List.of();
        }

        List<String> addresseeIds = sentRequests.stream()
            .map(Friendship::getAddresseeId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        Map<String, UserAccount> addresseeById = new HashMap<>();
        for (UserAccount account : userAccountRepository.findAllById(addresseeIds)) {
            addresseeById.put(account.getId(), account);
        }

        List<SentRequestView> result = new ArrayList<>();
        for (Friendship friendship : sentRequests) {
            if (friendship == null) {
                continue;
            }
            UserAccount addressee = addresseeById.get(friendship.getAddresseeId());
            result.add(new SentRequestView(
                friendship.getId(),
                friendship.getRequesterId(),
                friendship.getAddresseeId(),
                displayNameOf(addressee, friendship.getAddresseeId()),
                emailOf(addressee),
                avatarPathOf(addressee)
            ));
        }
        return result;
    }

    private String displayNameOf(UserAccount account, String fallback) {
        if (account == null) {
            return fallback == null ? "" : fallback;
        }
        String displayName = account.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        String email = account.getEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        return fallback == null ? "" : fallback;
    }

    private String emailOf(UserAccount account) {
        if (account == null || account.getEmail() == null) {
            return "";
        }
        return account.getEmail();
    }

    private String avatarPathOf(UserAccount account) {
        if (account == null || account.getAvatarPath() == null || account.getAvatarPath().isBlank()) {
            return "/uploads/avatars/default-avatar.jpg";
        }
        return account.getAvatarPath();
    }

    private FriendView toFriendView(UserAccount account) {
        if (account == null || account.getId() == null || account.getId().isBlank()) {
            return null;
        }
        return new FriendView(
            account.getId(),
            displayNameOf(account, account.getId()),
            emailOf(account),
            avatarPathOf(account),
            account.getScore(),
            account.isOnline()
        );
    }

    private String resolveCurrentUserId(String currentUserId) {
        if (currentUserId != null && !currentUserId.isBlank()
            && userAccountRepository.findById(currentUserId).isPresent()) {
            return currentUserId;
        }
        return "";
    }

    private String resolveCurrentUserId(String currentUserId, HttpServletRequest request) {
        String sessionUserId = requireSessionUserId(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }
        return resolveCurrentUserId(currentUserId);
    }

    private String requireSessionUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("AUTH_USER_ID");
        if (value == null) {
            return null;
        }
        String userId = String.valueOf(value).trim();
        if (userId.isEmpty()) {
            return null;
        }
        return userId;
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

    public record FriendRequestView(Long friendshipId,
                                    String requesterId,
                                    String requesterName,
                                    String requesterEmail,
                                    String requesterAvatarPath,
                                    String addresseeId) {
    }

    public record FriendView(String userId,
                             String displayName,
                             String email,
                             String avatarPath,
                             int score,
                             boolean online) {
    }

    public record SentRequestView(Long friendshipId,
                                  String requesterId,
                                  String addresseeId,
                                  String addresseeName,
                                  String addresseeEmail,
                                  String addresseeAvatarPath) {
    }
}
