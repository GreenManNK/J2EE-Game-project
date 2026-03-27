package com.game.hub.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.entity.Friendship;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.FriendshipRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AccountSyncService {
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
    private static final Set<String> ALLOWED_THEME_MODES = Set.of("system", "light", "dark");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("vi", "en");
    private static final Set<Integer> ALLOWED_FRIEND_REFRESH_MS = Set.of(5000, 10000, 15000, 20000, 30000, 60000);
    private static final String GAME_CODE_CHESS_OFFLINE = "chess-offline";
    private static final String GAME_CODE_XIANGQI_OFFLINE = "xiangqi-offline";
    private static final String GAME_CODE_MINESWEEPER = "minesweeper";
    private static final String GAME_CODE_QUIZ_PRACTICE = "quiz-practice";
    private static final String GAME_CODE_TYPING_PRACTICE = "typing-practice";
    private static final List<String> SUPPORTED_GAME_CODES = List.of(
        GAME_CODE_CHESS_OFFLINE,
        GAME_CODE_XIANGQI_OFFLINE,
        GAME_CODE_MINESWEEPER,
        GAME_CODE_QUIZ_PRACTICE,
        GAME_CODE_TYPING_PRACTICE
    );

    private final UserAccountRepository userAccountRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final FriendshipRepository friendshipRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AccountService accountService;

    public AccountSyncService(UserAccountRepository userAccountRepository,
                              GameHistoryRepository gameHistoryRepository,
                              UserAchievementRepository userAchievementRepository,
                              FriendshipRepository friendshipRepository,
                              PasswordEncoder passwordEncoder,
                              ObjectMapper objectMapper,
                              AccountService accountService) {
        this.userAccountRepository = userAccountRepository;
        this.gameHistoryRepository = gameHistoryRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.friendshipRepository = friendshipRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.accountService = accountService;
    }

    @Transactional
    public AccountService.ServiceResult upsertAccount(AccountSyncRequest request) {
        AccountService.ServiceResult preparedResult = prepareAccountUpsert(request);
        if (!preparedResult.success()) {
            return preparedResult;
        }

        PreparedUpsert prepared = (PreparedUpsert) preparedResult.data();
        AccountService.ServiceResult relatedResult = applyRelatedData(prepared);
        if (!relatedResult.success()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return relatedResult;
        }

        return AccountService.ServiceResult.ok(buildUpsertResponse(prepared));
    }

    @Transactional
    public AccountService.ServiceResult upsertAccountsBulk(BulkAccountSyncRequest request) {
        if (request == null || request.accounts() == null || request.accounts().isEmpty()) {
            return AccountService.ServiceResult.error("accounts is required");
        }

        boolean continueOnError = Boolean.TRUE.equals(request.continueOnError());
        List<BulkPreparedEntry> preparedEntries = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (int index = 0; index < request.accounts().size(); index++) {
            AccountSyncRequest accountRequest = request.accounts().get(index);
            AccountService.ServiceResult preparedResult = prepareAccountUpsert(accountRequest);
            if (!preparedResult.success()) {
                errorCount++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", index);
                item.put("success", false);
                item.put("error", preparedResult.error());
                results.add(item);
                if (!continueOnError) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return AccountService.ServiceResult.error("Bulk sync failed at account index " + index + ": " + preparedResult.error());
                }
                continue;
            }

            PreparedUpsert prepared = (PreparedUpsert) preparedResult.data();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("success", true);
            item.put("userId", prepared.user().getId());
            item.put("email", prepared.user().getEmail());
            item.put("created", prepared.created());
            item.put("relatedDataApplied", false);
            results.add(item);
            preparedEntries.add(new BulkPreparedEntry(index, prepared, item));
        }

        for (BulkPreparedEntry entry : preparedEntries) {
            AccountService.ServiceResult relatedResult = applyRelatedData(entry.prepared());
            if (!relatedResult.success()) {
                errorCount++;
                entry.responseItem().put("success", false);
                entry.responseItem().put("relatedDataApplied", false);
                entry.responseItem().put("error", relatedResult.error());
                if (!continueOnError) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return AccountService.ServiceResult.error("Bulk sync failed at account index " + entry.index() + ": " + relatedResult.error());
                }
                continue;
            }

            successCount++;
            entry.responseItem().put("relatedDataApplied", true);
            entry.responseItem().put("account", buildAccountSnapshot(entry.prepared().user().getId()));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("successCount", successCount);
        response.put("errorCount", errorCount);
        response.put("continueOnError", continueOnError);
        response.put("results", results);
        return AccountService.ServiceResult.ok(response);
    }

    @Transactional(readOnly = true)
    public AccountService.ServiceResult exportAccountSnapshots(List<String> userIds, List<String> emails) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        List<String> missingUserIds = new ArrayList<>();
        List<String> missingEmails = new ArrayList<>();

        if ((userIds == null || userIds.isEmpty()) && (emails == null || emails.isEmpty())) {
            List<UserAccount> users = userAccountRepository.findAll().stream()
                .sorted(Comparator.comparing(user -> defaultIfBlank(user.getEmail(), user.getId()), String.CASE_INSENSITIVE_ORDER))
                .toList();
            for (UserAccount user : users) {
                accounts.add(buildAccountSnapshot(user.getId()));
            }
        } else {
            Set<String> exportedUserIds = new HashSet<>();

            for (String userId : userIds == null ? List.<String>of() : userIds) {
                String normalizedUserId = trimToNull(userId);
                if (normalizedUserId == null || !exportedUserIds.add(normalizedUserId)) {
                    continue;
                }
                UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
                if (user == null) {
                    missingUserIds.add(normalizedUserId);
                    continue;
                }
                accounts.add(buildAccountSnapshot(user.getId()));
            }

            for (String email : emails == null ? List.<String>of() : emails) {
                String normalizedEmail = normalizeEmail(email);
                if (normalizedEmail == null) {
                    continue;
                }
                UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
                if (user == null) {
                    missingEmails.add(normalizedEmail);
                    continue;
                }
                if (exportedUserIds.add(user.getId())) {
                    accounts.add(buildAccountSnapshot(user.getId()));
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", accounts.size());
        response.put("accounts", accounts);
        response.put("missingUserIds", missingUserIds);
        response.put("missingEmails", missingEmails);
        return AccountService.ServiceResult.ok(response);
    }

    private AccountService.ServiceResult prepareAccountUpsert(AccountSyncRequest request) {
        if (request == null) {
            return AccountService.ServiceResult.error("Invalid request");
        }

        String normalizedUserId = trimToNull(request.userId());
        String normalizedEmail = normalizeEmail(request.email());
        ResolvedUser resolvedUser = resolveUser(normalizedUserId, normalizedEmail);
        if (resolvedUser.error() != null) {
            return AccountService.ServiceResult.error(resolvedUser.error());
        }

        boolean created = resolvedUser.user() == null;
        UserAccount user = created ? new UserAccount() : resolvedUser.user();
        if (created && normalizedUserId != null) {
            user.setId(normalizedUserId);
        }

        if (normalizedEmail == null && created) {
            return AccountService.ServiceResult.error("Email is required");
        }
        if (normalizedEmail != null) {
            UserAccount existingByEmail = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
            if (existingByEmail != null && !Objects.equals(existingByEmail.getId(), user.getId())) {
                return AccountService.ServiceResult.error("Email already exists");
            }
            user.setEmail(normalizedEmail);
            user.setUsername(normalizedEmail);
        }

        String password = trimToNull(request.password());
        String passwordHash = trimToNull(request.passwordHash());
        if (password != null && passwordHash != null) {
            return AccountService.ServiceResult.error("Provide password or passwordHash, not both");
        }
        if (passwordHash != null) {
            user.setPassword(passwordHash);
        } else if (password != null) {
            user.setPassword(passwordEncoder.encode(password));
        } else if (created) {
            return AccountService.ServiceResult.error("Password is required for new account");
        }

        String displayName = trimToNull(request.displayName());
        if (displayName == null) {
            displayName = created ? defaultDisplayNameFromEmail(normalizedEmail) : trimToNull(user.getDisplayName());
        }
        user.setDisplayName(displayName == null ? "Player" : displayName);

        String avatarPath = trimToNull(request.avatarPath());
        user.setAvatarPath(avatarPath == null
            ? defaultIfBlank(user.getAvatarPath(), DEFAULT_AVATAR_PATH)
            : avatarPath);

        if (request.emailConfirmed() != null) {
            user.setEmailConfirmed(request.emailConfirmed());
        } else if (created) {
            user.setEmailConfirmed(false);
        }

        String role = trimToNull(request.role());
        if (role != null) {
            user.setRole(role);
        } else if (created) {
            user.setRole("User");
        }

        if (request.score() != null) {
            user.setScore(Math.max(0, request.score()));
        }
        if (request.highestScore() != null) {
            user.setHighestScore(Math.max(0, request.highestScore()));
        } else if (created) {
            user.setHighestScore(Math.max(0, user.getScore()));
        }
        if (user.getHighestScore() < user.getScore()) {
            user.setHighestScore(user.getScore());
        }

        if (request.online() != null) {
            user.setOnline(request.online());
        }
        if (request.bannedUntil() != null || request.clearBannedUntil() != null) {
            user.setBannedUntil(Boolean.TRUE.equals(request.clearBannedUntil()) ? null : request.bannedUntil());
        }
        if (request.lastSystemNotificationSeenAt() != null) {
            user.setLastSystemNotificationSeenAt(request.lastSystemNotificationSeenAt());
        }

        applyPreferences(user, request.preferences());
        try {
            applyGameStats(user, request.gameStats(), request.replaceRelatedData());
        } catch (IllegalArgumentException ex) {
            return AccountService.ServiceResult.error(ex.getMessage());
        }
        userAccountRepository.save(user);
        return AccountService.ServiceResult.ok(new PreparedUpsert(user, created, request));
    }

    private AccountService.ServiceResult applyRelatedData(PreparedUpsert prepared) {
        applyAchievements(prepared.user().getId(), prepared.request().achievements(), prepared.request().replaceRelatedData());
        applyGameHistory(prepared.user().getId(), prepared.request().gameHistory(), prepared.request().replaceRelatedData());
        AccountService.ServiceResult friendshipResult =
            applyFriendships(prepared.user().getId(), prepared.request().friendships(), prepared.request().replaceRelatedData());
        if (!friendshipResult.success()) {
            return friendshipResult;
        }
        if (prepared.request().gamesBrowserState() != null) {
            AccountService.ServiceResult gamesBrowserResult = accountService.updateGamesBrowserState(
                prepared.user().getId(),
                new AccountService.GamesBrowserStateRequest(
                    prepared.request().gamesBrowserState().favorites(),
                    prepared.request().gamesBrowserState().recentGames(),
                    Boolean.valueOf(prepared.request().replaceRelatedData() != null && !prepared.request().replaceRelatedData())
                )
            );
            if (!gamesBrowserResult.success()) {
                return gamesBrowserResult;
            }
        }
        if (prepared.request().puzzleCatalogState() != null) {
            return accountService.updatePuzzleCatalogState(
                prepared.user().getId(),
                new AccountService.PuzzleCatalogStateRequest(
                    prepared.request().puzzleCatalogState().favorites(),
                    prepared.request().puzzleCatalogState().ratings(),
                    prepared.request().puzzleCatalogState().recentCodes(),
                    Boolean.valueOf(prepared.request().replaceRelatedData() != null && !prepared.request().replaceRelatedData())
                )
            );
        }
        return AccountService.ServiceResult.ok(Map.of());
    }

    private Map<String, Object> buildUpsertResponse(PreparedUpsert prepared) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("created", prepared.created());
        response.put("replaceRelatedData", prepared.request().replaceRelatedData() == null || prepared.request().replaceRelatedData());
        response.put("account", buildAccountSnapshot(prepared.user().getId()));
        return response;
    }

    @Transactional(readOnly = true)
    public AccountService.ServiceResult getAccountSnapshot(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return AccountService.ServiceResult.error("userId is required");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return AccountService.ServiceResult.error("User not found");
        }
        return AccountService.ServiceResult.ok(buildAccountSnapshot(user.getId()));
    }

    @Transactional(readOnly = true)
    public AccountService.ServiceResult getAccountSnapshotByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return AccountService.ServiceResult.error("Email is required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return AccountService.ServiceResult.error("User not found");
        }
        return AccountService.ServiceResult.ok(buildAccountSnapshot(user.getId()));
    }

    private ResolvedUser resolveUser(String requestedUserId, String requestedEmail) {
        UserAccount byId = requestedUserId == null ? null : userAccountRepository.findById(requestedUserId).orElse(null);
        UserAccount byEmail = requestedEmail == null ? null : userAccountRepository.findByEmail(requestedEmail).orElse(null);

        if (byId != null && byEmail != null && !Objects.equals(byId.getId(), byEmail.getId())) {
            return new ResolvedUser(null, "userId does not match email");
        }
        if (byId == null && requestedUserId != null && byEmail != null && !requestedUserId.equals(byEmail.getId())) {
            return new ResolvedUser(null, "userId does not match email");
        }
        return new ResolvedUser(byId != null ? byId : byEmail, null);
    }

    private void applyPreferences(UserAccount user, PreferencesPayload preferences) {
        if (user == null || preferences == null) {
            return;
        }
        user.setThemeMode(normalizeThemeMode(preferences.themeMode(), user.getThemeMode()));
        user.setLanguage(normalizeLanguage(preferences.language(), user.getLanguage()));
        user.setSidebarDesktopVisibleByDefault(valueOrDefault(
            preferences.sidebarDesktopVisibleByDefault(),
            user.isSidebarDesktopVisibleByDefault()
        ));
        user.setSidebarMobileAutoClose(valueOrDefault(
            preferences.sidebarMobileAutoClose(),
            user.isSidebarMobileAutoClose()
        ));
        user.setHomeMusicEnabled(valueOrDefault(
            preferences.homeMusicEnabled(),
            user.isHomeMusicEnabled()
        ));
        user.setToastNotificationsEnabled(valueOrDefault(
            preferences.toastNotificationsEnabled(),
            user.isToastNotificationsEnabled()
        ));
        user.setShowOfflineFriendsInSidebar(valueOrDefault(
            preferences.showOfflineFriendsInSidebar(),
            user.isShowOfflineFriendsInSidebar()
        ));
        user.setAutoRefreshFriendList(valueOrDefault(
            preferences.autoRefreshFriendList(),
            user.isAutoRefreshFriendList()
        ));
        user.setFriendListRefreshMs(normalizeFriendRefreshMs(
            preferences.friendListRefreshMs(),
            user.getFriendListRefreshMs()
        ));
    }

    private void applyGameStats(UserAccount user, Map<String, Object> gameStats, Boolean replaceRelatedData) {
        if (user == null) {
            return;
        }
        boolean replace = replaceRelatedData == null || replaceRelatedData;
        Map<String, Object> safeGameStats = gameStats == null ? Map.of() : gameStats;
        Set<String> normalizedKeys = new HashSet<>();
        for (Map.Entry<String, Object> entry : safeGameStats.entrySet()) {
            String normalizedGameCode = normalizeGameCode(entry.getKey());
            if (normalizedGameCode == null) {
                throw new IllegalArgumentException("Unsupported gameCode: " + entry.getKey());
            }
            normalizedKeys.add(normalizedGameCode);

            Map<String, Object> incoming = normalizeStatsByGameCode(normalizedGameCode, entry.getValue());
            Map<String, Object> toPersist = replace
                ? incoming
                : mergeStatsByGameCode(normalizedGameCode, readStatsForGameCode(user, normalizedGameCode), incoming);
            writeStatsForGameCode(user, normalizedGameCode, toPersist);
        }

        if (replace && gameStats != null) {
            for (String supportedGameCode : SUPPORTED_GAME_CODES) {
                if (!normalizedKeys.contains(supportedGameCode)) {
                    writeStatsForGameCode(user, supportedGameCode, Map.of());
                }
            }
        }
    }

    private void applyAchievements(String userId, List<AchievementPayload> achievements, Boolean replaceRelatedData) {
        if (userId == null) {
            return;
        }
        boolean replace = replaceRelatedData == null || replaceRelatedData;
        List<AchievementPayload> safeAchievements = achievements == null ? List.of() : achievements;

        if (replace) {
            userAchievementRepository.deleteAll(userAchievementRepository.findByUserId(userId));
        }

        Set<String> existingKeys = new HashSet<>();
        if (!replace) {
            for (UserAchievement achievement : userAchievementRepository.findByUserId(userId)) {
                existingKeys.add(achievementKey(achievement.getAchievementName(), achievement.getAchievedAt()));
            }
        }

        List<UserAchievement> toPersist = new ArrayList<>();
        for (AchievementPayload payload : safeAchievements) {
            String achievementName = trimToNull(payload == null ? null : payload.achievementName());
            if (achievementName == null) {
                continue;
            }
            LocalDateTime achievedAt = payload.achievedAt() == null ? LocalDateTime.now() : payload.achievedAt();
            String key = achievementKey(achievementName, achievedAt);
            if (!replace && existingKeys.contains(key)) {
                continue;
            }
            existingKeys.add(key);

            UserAchievement achievement = new UserAchievement();
            achievement.setUserId(userId);
            achievement.setAchievementName(achievementName);
            achievement.setAchievedAt(achievedAt);
            toPersist.add(achievement);
        }
        if (!toPersist.isEmpty()) {
            userAchievementRepository.saveAll(toPersist);
        }
    }

    private void applyGameHistory(String userId, List<GameHistoryPayload> gameHistory, Boolean replaceRelatedData) {
        if (userId == null) {
            return;
        }
        boolean replace = replaceRelatedData == null || replaceRelatedData;
        List<GameHistoryPayload> safeHistory = gameHistory == null ? List.of() : gameHistory;

        if (replace) {
            gameHistoryRepository.deleteAll(gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId));
        }

        Set<String> existingKeys = new HashSet<>();
        if (!replace) {
            for (GameHistory history : gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId)) {
                existingKeys.add(historyKey(history.getGameCode(), history.getPlayer1Id(), history.getPlayer2Id(),
                    history.getFirstPlayerId(), history.getWinnerId(), history.getTotalMoves(), history.getPlayedAt()));
            }
        }

        List<GameHistory> toPersist = new ArrayList<>();
        for (GameHistoryPayload payload : safeHistory) {
            if (payload == null) {
                continue;
            }
            String gameCode = trimToNull(payload.gameCode());
            String player1Id = trimToNull(payload.player1Id());
            String player2Id = trimToNull(payload.player2Id());
            if (gameCode == null || (player1Id == null && player2Id == null)) {
                continue;
            }
            if (!userId.equals(player1Id) && !userId.equals(player2Id)) {
                continue;
            }

            String key = historyKey(gameCode, player1Id, player2Id, trimToNull(payload.firstPlayerId()),
                trimToNull(payload.winnerId()), Math.max(0, valueOrZero(payload.totalMoves())),
                payload.playedAt() == null ? LocalDateTime.now() : payload.playedAt());
            if (!replace && existingKeys.contains(key)) {
                continue;
            }
            existingKeys.add(key);

            GameHistory history = new GameHistory();
            history.setGameCode(gameCode);
            history.setMatchCode(trimToNull(payload.matchCode()));
            history.setRoomId(trimToNull(payload.roomId()));
            history.setLocationLabel(trimToNull(payload.locationLabel()));
            history.setLocationPath(trimToNull(payload.locationPath()));
            history.setPlayer1Id(player1Id);
            history.setPlayer2Id(player2Id);
            history.setFirstPlayerId(trimToNull(payload.firstPlayerId()));
            history.setWinnerId(trimToNull(payload.winnerId()));
            history.setTotalMoves(Math.max(0, valueOrZero(payload.totalMoves())));
            history.setPlayedAt(payload.playedAt() == null ? LocalDateTime.now() : payload.playedAt());
            toPersist.add(history);
        }
        if (!toPersist.isEmpty()) {
            gameHistoryRepository.saveAll(toPersist);
        }
    }

    private AccountService.ServiceResult applyFriendships(String userId,
                                                          List<FriendshipPayload> friendships,
                                                          Boolean replaceRelatedData) {
        if (userId == null) {
            return AccountService.ServiceResult.ok(Map.of());
        }
        boolean replace = replaceRelatedData == null || replaceRelatedData;
        List<FriendshipPayload> safeFriendships = friendships == null ? List.of() : friendships;

        if (replace) {
            friendshipRepository.deleteAll(friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId));
        }

        Set<String> existingKeys = new HashSet<>();
        if (!replace) {
            for (Friendship friendship : friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId)) {
                existingKeys.add(friendshipKey(friendship.getRequesterId(), friendship.getAddresseeId()));
            }
        }

        List<Friendship> toPersist = new ArrayList<>();
        for (FriendshipPayload payload : safeFriendships) {
            if (payload == null) {
                continue;
            }
            String requesterId = trimToNull(payload.requesterId());
            String addresseeId = trimToNull(payload.addresseeId());
            if (requesterId == null || addresseeId == null || requesterId.equals(addresseeId)) {
                continue;
            }
            if (!userId.equals(requesterId) && !userId.equals(addresseeId)) {
                return AccountService.ServiceResult.error("Friendship payload must include the synced user");
            }
            if (userAccountRepository.findById(requesterId).isEmpty()) {
                return AccountService.ServiceResult.error("Requester account not found: " + requesterId);
            }
            if (userAccountRepository.findById(addresseeId).isEmpty()) {
                return AccountService.ServiceResult.error("Addressee account not found: " + addresseeId);
            }

            String key = friendshipKey(requesterId, addresseeId);
            String reverseKey = friendshipKey(addresseeId, requesterId);
            if (!replace && (existingKeys.contains(key) || existingKeys.contains(reverseKey))) {
                continue;
            }
            existingKeys.add(key);

            Friendship friendship = new Friendship();
            friendship.setRequesterId(requesterId);
            friendship.setAddresseeId(addresseeId);
            friendship.setAccepted(payload.accepted() == null || payload.accepted());
            toPersist.add(friendship);
        }
        if (!toPersist.isEmpty()) {
            friendshipRepository.saveAll(toPersist);
        }
        return AccountService.ServiceResult.ok(Map.of());
    }

    private Map<String, Object> buildAccountSnapshot(String userId) {
        UserAccount user = userAccountRepository.findById(userId).orElseThrow();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("userId", user.getId());
        snapshot.put("email", defaultIfBlank(user.getEmail(), ""));
        snapshot.put("username", defaultIfBlank(user.getUsername(), defaultIfBlank(user.getEmail(), "")));
        snapshot.put("displayName", defaultIfBlank(user.getDisplayName(), "Player"));
        snapshot.put("avatarPath", defaultIfBlank(user.getAvatarPath(), DEFAULT_AVATAR_PATH));
        snapshot.put("role", defaultIfBlank(user.getRole(), "User"));
        snapshot.put("emailConfirmed", user.isEmailConfirmed());
        snapshot.put("score", user.getScore());
        snapshot.put("highestScore", user.getHighestScore());
        snapshot.put("online", user.isOnline());
        snapshot.put("bannedUntil", user.getBannedUntil());
        snapshot.put("lastSystemNotificationSeenAt", user.getLastSystemNotificationSeenAt());
        snapshot.put("hasPassword", trimToNull(user.getPassword()) != null);
        snapshot.put("preferences", buildPreferencesPayload(user));
        snapshot.put("gameStats", buildGameStatsPayload(user));
        snapshot.put("gamesBrowserState", accountService.getGamesBrowserState(userId).data());
        snapshot.put("puzzleCatalogState", accountService.getPuzzleCatalogState(userId).data());
        snapshot.put("achievements", buildAchievementsPayload(userId));
        snapshot.put("gameHistory", buildGameHistoryPayload(userId));
        snapshot.put("friendships", buildFriendshipsPayload(userId));
        return snapshot;
    }

    private Map<String, Object> buildPreferencesPayload(UserAccount user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("themeMode", normalizeThemeMode(user.getThemeMode(), "system"));
        payload.put("language", normalizeLanguage(user.getLanguage(), "vi"));
        payload.put("sidebarDesktopVisibleByDefault", user.isSidebarDesktopVisibleByDefault());
        payload.put("sidebarMobileAutoClose", user.isSidebarMobileAutoClose());
        payload.put("homeMusicEnabled", user.isHomeMusicEnabled());
        payload.put("toastNotificationsEnabled", user.isToastNotificationsEnabled());
        payload.put("showOfflineFriendsInSidebar", user.isShowOfflineFriendsInSidebar());
        payload.put("autoRefreshFriendList", user.isAutoRefreshFriendList());
        payload.put("friendListRefreshMs", normalizeFriendRefreshMs(user.getFriendListRefreshMs(), 5000));
        return payload;
    }

    private Map<String, Object> buildGameStatsPayload(UserAccount user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(GAME_CODE_CHESS_OFFLINE, normalizeChessStats(readStatsForGameCode(user, GAME_CODE_CHESS_OFFLINE)));
        payload.put(GAME_CODE_XIANGQI_OFFLINE, normalizeXiangqiStats(readStatsForGameCode(user, GAME_CODE_XIANGQI_OFFLINE)));
        payload.put(GAME_CODE_MINESWEEPER, normalizeMinesweeperStats(readStatsForGameCode(user, GAME_CODE_MINESWEEPER)));
        payload.put(GAME_CODE_QUIZ_PRACTICE, normalizeQuizPracticeStats(readStatsForGameCode(user, GAME_CODE_QUIZ_PRACTICE)));
        payload.put(GAME_CODE_TYPING_PRACTICE, normalizeTypingPracticeStats(readStatsForGameCode(user, GAME_CODE_TYPING_PRACTICE)));
        return payload;
    }

    private List<Map<String, Object>> buildAchievementsPayload(String userId) {
        return userAchievementRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(UserAchievement::getAchievedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
            .map(achievement -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("achievementName", achievement.getAchievementName());
                payload.put("achievedAt", achievement.getAchievedAt());
                return payload;
            })
            .toList();
    }

    private List<Map<String, Object>> buildGameHistoryPayload(String userId) {
        return gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId).stream()
            .sorted(Comparator.comparing(GameHistory::getPlayedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
            .map(history -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", history.getId());
                payload.put("gameCode", history.getGameCode());
                payload.put("matchCode", history.getMatchCode());
                payload.put("roomId", history.getRoomId());
                payload.put("locationLabel", history.getLocationLabel());
                payload.put("locationPath", history.getLocationPath());
                payload.put("player1Id", history.getPlayer1Id());
                payload.put("player2Id", history.getPlayer2Id());
                payload.put("firstPlayerId", history.getFirstPlayerId());
                payload.put("winnerId", history.getWinnerId());
                payload.put("totalMoves", history.getTotalMoves());
                payload.put("playedAt", history.getPlayedAt());
                return payload;
            })
            .toList();
    }

    private List<Map<String, Object>> buildFriendshipsPayload(String userId) {
        return friendshipRepository.findByRequesterIdOrAddresseeId(userId, userId).stream()
            .sorted(Comparator.comparing(Friendship::isAccepted).reversed().thenComparing(Friendship::getId, Comparator.nullsLast(Long::compareTo)).reversed())
            .map(friendship -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", friendship.getId());
                payload.put("requesterId", friendship.getRequesterId());
                payload.put("addresseeId", friendship.getAddresseeId());
                payload.put("accepted", friendship.isAccepted());
                return payload;
            })
            .toList();
    }

    private Map<String, Object> readStatsForGameCode(UserAccount user, String gameCode) {
        if (user == null || gameCode == null) {
            return Map.of();
        }
        String rawJson;
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            rawJson = user.getChessOfflineStatsJson();
        } else if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            rawJson = user.getXiangqiOfflineStatsJson();
        } else if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            rawJson = user.getMinesweeperStatsJson();
        } else if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            rawJson = user.getQuizPracticeStatsJson();
        } else if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            rawJson = user.getTypingPracticeStatsJson();
        } else {
            rawJson = null;
        }
        return readJsonMap(rawJson);
    }

    private void writeStatsForGameCode(UserAccount user, String gameCode, Map<String, Object> stats) {
        String json = writeJson(stats);
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            user.setChessOfflineStatsJson(json);
            return;
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            user.setXiangqiOfflineStatsJson(json);
            return;
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            user.setMinesweeperStatsJson(json);
            return;
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            user.setQuizPracticeStatsJson(json);
            return;
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            user.setTypingPracticeStatsJson(json);
            return;
        }
        throw new IllegalArgumentException("Unsupported gameCode: " + gameCode);
    }

    private String normalizeGameCode(String gameCode) {
        String normalized = trimToNull(gameCode);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        if ("chess-offline".equals(lowered) || "chess_offline".equals(lowered)
            || "chessoffline".equals(lowered) || "chess".equals(lowered)) {
            return GAME_CODE_CHESS_OFFLINE;
        }
        if ("xiangqi-offline".equals(lowered) || "xiangqi_offline".equals(lowered)
            || "xiangqioffline".equals(lowered) || "xiangqi".equals(lowered)) {
            return GAME_CODE_XIANGQI_OFFLINE;
        }
        if ("minesweeper".equals(lowered)) {
            return GAME_CODE_MINESWEEPER;
        }
        if ("quiz-practice".equals(lowered) || "quiz_practice".equals(lowered)
            || "quizpractice".equals(lowered) || "quiz-local".equals(lowered)
            || "quiz_local".equals(lowered) || "quizlocal".equals(lowered)
            || "quiz-bot".equals(lowered) || "quiz_bot".equals(lowered)
            || "quizbot".equals(lowered) || "quiz".equals(lowered)) {
            return GAME_CODE_QUIZ_PRACTICE;
        }
        if ("typing-practice".equals(lowered) || "typing_practice".equals(lowered)
            || "typingpractice".equals(lowered) || "typing-local".equals(lowered)
            || "typing_local".equals(lowered) || "typinglocal".equals(lowered)
            || "typing-bot".equals(lowered) || "typing_bot".equals(lowered)
            || "typingbot".equals(lowered) || "typing".equals(lowered)) {
            return GAME_CODE_TYPING_PRACTICE;
        }
        return null;
    }

    private Map<String, Object> normalizeStatsByGameCode(String gameCode, Object rawStats) {
        Map<String, Object> source = asStringObjectMap(rawStats);
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return normalizeChessStats(source);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return normalizeXiangqiStats(source);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return normalizeMinesweeperStats(source);
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            return normalizeQuizPracticeStats(source);
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            return normalizeTypingPracticeStats(source);
        }
        return Map.of();
    }

    private Map<String, Object> mergeStatsByGameCode(String gameCode,
                                                     Map<String, Object> existing,
                                                     Map<String, Object> incoming) {
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return mergeChessStats(existing, incoming);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return mergeXiangqiStats(existing, incoming);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return mergeMinesweeperStats(existing, incoming);
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            return mergeQuizPracticeStats(existing, incoming);
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            return mergeTypingPracticeStats(existing, incoming);
        }
        return Map.of();
    }

    private Map<String, Object> normalizeChessStats(Map<String, Object> source) {
        int whiteWins = Math.max(0, toInt(source.get("whiteWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "whiteWins", whiteWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeXiangqiStats(Map<String, Object> source) {
        int redWins = Math.max(0, toInt(source.get("redWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "redWins", redWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeMinesweeperStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), Math.max(0, totalGames - wins)));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> bestTimes = normalizeBestTimesMap(source.get("bestTimes"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", bestTimes);
        return result;
    }

    private Map<String, Object> normalizeQuizPracticeStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        int bestScore = Math.max(0, toInt(source.get("bestScore"), 0));
        int perfectRounds = Math.max(0, toInt(source.get("perfectRounds"), 0));
        totalGames = Math.max(totalGames, wins + losses + draws);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("draws", draws);
        result.put("bestScore", bestScore);
        result.put("perfectRounds", perfectRounds);
        return result;
    }

    private Map<String, Object> normalizeTypingPracticeStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        int bestWpm = Math.max(0, toInt(source.get("bestWpm"), 0));
        double bestAccuracy = normalizePercentage(source.get("bestAccuracy"));
        int completedQuotes = Math.max(0, toInt(source.get("completedQuotes"), 0));
        totalGames = Math.max(totalGames, wins + losses + draws);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("draws", draws);
        result.put("bestWpm", bestWpm);
        result.put("bestAccuracy", bestAccuracy);
        result.put("completedQuotes", completedQuotes);
        return result;
    }

    private Map<String, Object> mergeChessStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeChessStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeChessStats(asStringObjectMap(incoming));
        return Map.of(
            "whiteWins", Math.max(toInt(safeExisting.get("whiteWins"), 0), toInt(safeIncoming.get("whiteWins"), 0)),
            "blackWins", Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0)),
            "draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0))
        );
    }

    private Map<String, Object> mergeXiangqiStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeXiangqiStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeXiangqiStats(asStringObjectMap(incoming));
        return Map.of(
            "redWins", Math.max(toInt(safeExisting.get("redWins"), 0), toInt(safeIncoming.get("redWins"), 0)),
            "blackWins", Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0)),
            "draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0))
        );
    }

    private Map<String, Object> mergeMinesweeperStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeMinesweeperStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeMinesweeperStats(asStringObjectMap(incoming));

        int wins = Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0));
        int losses = Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0));
        int totalGames = Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> mergedBestTimes = normalizeBestTimesMap(safeExisting.get("bestTimes"));
        for (Map.Entry<String, Integer> entry : normalizeBestTimesMap(safeIncoming.get("bestTimes")).entrySet()) {
            Integer current = mergedBestTimes.get(entry.getKey());
            if (current == null || entry.getValue() < current) {
                mergedBestTimes.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", mergedBestTimes);
        return result;
    }

    private Map<String, Object> mergeQuizPracticeStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeQuizPracticeStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeQuizPracticeStats(asStringObjectMap(incoming));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0)));
        result.put("wins", Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0)));
        result.put("losses", Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0)));
        result.put("draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0)));
        result.put("bestScore", Math.max(toInt(safeExisting.get("bestScore"), 0), toInt(safeIncoming.get("bestScore"), 0)));
        result.put("perfectRounds", Math.max(toInt(safeExisting.get("perfectRounds"), 0), toInt(safeIncoming.get("perfectRounds"), 0)));
        return normalizeQuizPracticeStats(result);
    }

    private Map<String, Object> mergeTypingPracticeStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeTypingPracticeStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeTypingPracticeStats(asStringObjectMap(incoming));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGames", Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0)));
        result.put("wins", Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0)));
        result.put("losses", Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0)));
        result.put("draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0)));
        result.put("bestWpm", Math.max(toInt(safeExisting.get("bestWpm"), 0), toInt(safeIncoming.get("bestWpm"), 0)));
        result.put("bestAccuracy", Math.max(normalizePercentage(safeExisting.get("bestAccuracy")), normalizePercentage(safeIncoming.get("bestAccuracy"))));
        result.put("completedQuotes", Math.max(toInt(safeExisting.get("completedQuotes"), 0), toInt(safeIncoming.get("completedQuotes"), 0)));
        return normalizeTypingPracticeStats(result);
    }

    private Map<String, Integer> normalizeBestTimesMap(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = trimToNull(String.valueOf(entry.getKey()));
            if (key == null) {
                continue;
            }
            int seconds = toInt(entry.getValue(), -1);
            if (seconds >= 0) {
                result.put(key, seconds);
            }
        }
        return result;
    }

    private Map<String, Object> asStringObjectMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> readJsonMap(String json) {
        String raw = trimToNull(json);
        if (raw == null) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot serialize account stats", ex);
        }
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double normalizePercentage(Object value) {
        if (value == null) {
            return 0.0;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return 0.0;
            }
            try {
                parsed = Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        double clamped = Math.max(0.0, Math.min(100.0, parsed));
        return Math.round(clamped * 10.0) / 10.0;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeThemeMode(String value, String fallback) {
        String normalized = trimToNull(value);
        String safeFallback = trimToNull(fallback);
        if (normalized == null) {
            return safeFallback == null ? "system" : normalizeThemeMode(safeFallback, null);
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_THEME_MODES.contains(lowered) ? lowered : (safeFallback == null ? "system" : normalizeThemeMode(safeFallback, null));
    }

    private String normalizeLanguage(String value, String fallback) {
        String normalized = trimToNull(value);
        String safeFallback = trimToNull(fallback);
        if (normalized == null) {
            return safeFallback == null ? "vi" : normalizeLanguage(safeFallback, null);
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_LANGUAGES.contains(lowered) ? lowered : (safeFallback == null ? "vi" : normalizeLanguage(safeFallback, null));
    }

    private int normalizeFriendRefreshMs(Integer requested, int fallback) {
        int value = requested == null ? fallback : requested;
        if (ALLOWED_FRIEND_REFRESH_MS.contains(value)) {
            return value;
        }
        return 5000;
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String defaultDisplayNameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Player";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "Player";
        }
        String prefix = trimToNull(email.substring(0, at));
        return prefix == null ? "Player" : prefix;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String achievementKey(String achievementName, LocalDateTime achievedAt) {
        return defaultIfBlank(achievementName, "") + "|" + String.valueOf(achievedAt);
    }

    private String historyKey(String gameCode,
                              String player1Id,
                              String player2Id,
                              String firstPlayerId,
                              String winnerId,
                              int totalMoves,
                              LocalDateTime playedAt) {
        return String.join("|",
            defaultIfBlank(gameCode, ""),
            defaultIfBlank(player1Id, ""),
            defaultIfBlank(player2Id, ""),
            defaultIfBlank(firstPlayerId, ""),
            defaultIfBlank(winnerId, ""),
            String.valueOf(totalMoves),
            String.valueOf(playedAt));
    }

    private String friendshipKey(String requesterId, String addresseeId) {
        return defaultIfBlank(requesterId, "") + "|" + defaultIfBlank(addresseeId, "");
    }

    private record PreparedUpsert(UserAccount user, boolean created, AccountSyncRequest request) {
    }

    private record BulkPreparedEntry(int index, PreparedUpsert prepared, Map<String, Object> responseItem) {
    }

    private record ResolvedUser(UserAccount user, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BulkAccountSyncRequest(List<AccountSyncRequest> accounts, Boolean continueOnError) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountSyncRequest(
        @JsonAlias({"id"}) String userId,
        String email,
        String displayName,
        String password,
        String passwordHash,
        String avatarPath,
        Boolean emailConfirmed,
        String role,
        Integer score,
        Integer highestScore,
        Boolean online,
        LocalDateTime bannedUntil,
        Boolean clearBannedUntil,
        LocalDateTime lastSystemNotificationSeenAt,
        PreferencesPayload preferences,
        Map<String, Object> gameStats,
        GamesBrowserStatePayload gamesBrowserState,
        PuzzleCatalogStatePayload puzzleCatalogState,
        List<AchievementPayload> achievements,
        List<GameHistoryPayload> gameHistory,
        List<FriendshipPayload> friendships,
        Boolean replaceRelatedData
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreferencesPayload(
        String themeMode,
        String language,
        Boolean sidebarDesktopVisibleByDefault,
        Boolean sidebarMobileAutoClose,
        Boolean homeMusicEnabled,
        Boolean toastNotificationsEnabled,
        Boolean showOfflineFriendsInSidebar,
        Boolean autoRefreshFriendList,
        Integer friendListRefreshMs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamesBrowserStatePayload(
        List<String> favorites,
        List<Map<String, Object>> recentGames
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PuzzleCatalogStatePayload(
        List<String> favorites,
        Map<String, Object> ratings,
        List<String> recentCodes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AchievementPayload(String achievementName, LocalDateTime achievedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameHistoryPayload(
        String gameCode,
        String matchCode,
        String roomId,
        String locationLabel,
        String locationPath,
        String player1Id,
        String player2Id,
        String firstPlayerId,
        Integer totalMoves,
        String winnerId,
        LocalDateTime playedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FriendshipPayload(String requesterId, String addresseeId, Boolean accepted) {
    }
}
