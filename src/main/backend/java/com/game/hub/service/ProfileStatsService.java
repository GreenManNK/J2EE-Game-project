package com.game.hub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.support.GameHistoryPresentationSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProfileStatsService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> POINT_BASED_ACHIEVEMENTS = Set.of("Bac", "Vang", "Kim cuong", "Thach dau");
    private static final List<String> GAME_ACHIEVEMENT_ORDER = List.of(
        "Caro",
        "Chess",
        "Xiangqi",
        "Tien Len",
        "Typing",
        "Quiz",
        "Blackjack",
        "Minesweeper",
        "Puzzle Jigsaw",
        "Puzzle Sliding",
        "Puzzle Word",
        "Puzzle Sudoku"
    );
    private static final List<String> OVERALL_ACHIEVEMENT_ORDER = List.of(
        "Khoi dong da game",
        "Chinh phuc 6 game",
        "Chinh phuc 9 game",
        "Bao trum Game Hub",
        "Hoan tat Puzzle Pack",
        "Dau tri thang Phat",
        "Chien thang khong tuong",
        "Chuoi bat bai",
        "Ha guc vua",
        "Chuoi thua huyen thoai",
        "Choi vi dam me",
        "1 phut lo la",
        "Ke sat than",
        "That bai xung dang",
        "Chien thang kho khan"
    );
    private static final List<String> ALL_POSSIBLE_ACHIEVEMENTS = List.of(
        "Winner - Caro",
        "Winner - Chess",
        "Winner - Xiangqi",
        "Winner - Tien Len",
        "Winner - Typing",
        "Winner - Blackjack",
        "Winner - Quiz",
        "Winner - Minesweeper",
        "Winner - Puzzle Jigsaw",
        "Winner - Puzzle Sliding",
        "Winner - Puzzle Word",
        "Winner - Puzzle Sudoku",
        "Bac",
        "Vang",
        "Kim cuong",
        "Thach dau",
        "Khoi dong da game",
        "Chinh phuc 6 game",
        "Chinh phuc 9 game",
        "Bao trum Game Hub",
        "Hoan tat Puzzle Pack",
        "Dau tri thang Phat",
        "Chien thang khong tuong",
        "Chuoi bat bai",
        "Ha guc vua",
        "Chuoi thua huyen thoai",
        "Choi vi dam me",
        "1 phut lo la",
        "Ke sat than",
        "That bai xung dang",
        "Chien thang kho khan",
        "Ke co chap",
        "Khong the ngan can"
    );
    private final GameHistoryRepository gameHistoryRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameCatalogService gameCatalogService;
    private final GameHistoryPresentationSupport gameHistoryPresentationSupport;

    public ProfileStatsService(GameHistoryRepository gameHistoryRepository,
                               UserAchievementRepository userAchievementRepository,
                               UserAccountRepository userAccountRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameCatalogService = new GameCatalogService();
        this.gameHistoryPresentationSupport = new GameHistoryPresentationSupport(this.gameCatalogService);
    }

    public Map<String, Object> buildProfileStats(String userId, String currentUserId) {
        UserAccount user = userAccountRepository.findById(userId).orElseThrow();
        List<GameHistory> allGames = gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId);
        boolean showAchievements = !isGuestUserId(userId);

        int totalGames = allGames.size();
        int winCount = (int) allGames.stream().filter(g -> userId.equals(g.getWinnerId())).count();
        double winRate = totalGames > 0 ? Math.round((winCount * 10000.0) / totalGames) / 100.0 : 0.0;

        int maxStreak = 0;
        int currentStreak = 0;
        for (int i = allGames.size() - 1; i >= 0; i--) {
            GameHistory g = allGames.get(i);
            if (userId.equals(g.getWinnerId())) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
        }

        int winFastRanked = (int) allGames.stream()
            .filter(g -> userId.equals(g.getWinnerId()) && g.getTotalMoves() < 15)
            .count();
        long memberDays = allGames.stream()
            .map(GameHistory::getPlayedAt)
            .filter(Objects::nonNull)
            .min(LocalDateTime::compareTo)
            .map(playedAt -> Math.max(0L, ChronoUnit.DAYS.between(playedAt.toLocalDate(), LocalDate.now())))
            .orElse(0L);

        List<UserAccount> leaderboard = userAccountRepository.findAllByOrderByScoreDesc();
        int rank = 1;
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getId().equals(userId)) {
                rank = i + 1;
                break;
            }
        }

        List<String> danhHieu = new ArrayList<>();
        if (rank == 1) danhHieu.add("Vua tro choi");
        else if (rank <= 3) danhHieu.add("Trum server");
        else if (rank <= 5) danhHieu.add("Thach dau");

        List<UserAchievement> userAchievements = showAchievements
            ? userAchievementRepository.findByUserId(userId)
            : List.of();
        Set<String> unlockedAchievementNames = userAchievements.stream()
            .map(UserAchievement::getAchievementName)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toCollection(HashSet::new));

        List<String> diemAchievements = userAchievements.stream()
            .map(UserAchievement::getAchievementName)
            .filter(POINT_BASED_ACHIEVEMENTS::contains)
            .distinct()
            .toList();

        Map<String, Long> repeatAchievements = new HashMap<>();
        for (UserAchievement a : userAchievements) {
            if (isRepeatAchievement(a.getAchievementName())) {
                repeatAchievements.merge(a.getAchievementName(), 1L, Long::sum);
            }
        }

        List<String> overallAchievements = OVERALL_ACHIEVEMENT_ORDER.stream()
            .filter(unlockedAchievementNames::contains)
            .toList();

        Map<String, List<String>> gameAchievements = buildGameAchievements(unlockedAchievementNames);

        Set<String> achieved = new HashSet<>();
        achieved.addAll(diemAchievements);
        achieved.addAll(overallAchievements);
        achieved.addAll(gameAchievements.keySet().stream().map(this::toGameAchievementName).toList());
        achieved.addAll(repeatAchievements.keySet());

        List<String> locked = showAchievements
            ? ALL_POSSIBLE_ACHIEVEMENTS.stream().filter(a -> !achieved.contains(a)).toList()
            : List.of();
        List<FavoriteGameView> favoriteGames = buildFavoriteGames(user);
        List<ActivityItem> recentActivity = buildRecentActivity(allGames, userId);
        List<PracticeStatCard> practiceStatCards = buildPracticeStatCards(user);
        long practiceProgressCount = practiceStatCards.stream().filter(PracticeStatCard::hasProgress).count();

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("totalGames", totalGames);
        result.put("winCount", winCount);
        result.put("winRate", winRate);
        result.put("rank", rank);
        result.put("danhHieu", danhHieu);
        result.put("winFastRanked", winFastRanked);
        result.put("bestStreak", maxStreak);
        result.put("currentStreak", currentStreak);
        result.put("memberDays", memberDays);
        result.put("favoriteGameCount", favoriteGames.size());
        result.put("favoriteGames", favoriteGames);
        result.put("recentActivity", recentActivity);
        result.put("practiceStatCards", practiceStatCards);
        result.put("practiceProgressCount", practiceProgressCount);
        result.put("showAchievements", showAchievements);
        result.put("diemAchievements", diemAchievements);
        result.put("overallAchievements", overallAchievements);
        result.put("gameAchievements", gameAchievements);
        result.put("repeatAchievements", repeatAchievements);
        result.put("achievements", overallAchievements);
        result.put("lockedAchievements", locked);
        result.put("isOwner", userId.equals(currentUserId));
        return result;
    }

    private Map<String, List<String>> buildGameAchievements(Set<String> unlockedAchievementNames) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        GAME_ACHIEVEMENT_ORDER.stream()
            .filter(gameName -> unlockedAchievementNames.contains(toGameAchievementName(gameName)))
            .forEach(gameName -> grouped.put(gameName, List.of("Chien thang dau tien")));
        return grouped;
    }

    private List<PracticeStatCard> buildPracticeStatCards(UserAccount user) {
        if (user == null) {
            return List.of();
        }
        return List.of(
            buildChessOfflineCard(user),
            buildXiangqiOfflineCard(user),
            buildMinesweeperCard(user),
            buildQuizPracticeCard(user),
            buildTypingPracticeCard(user)
        );
    }

    private PracticeStatCard buildChessOfflineCard(UserAccount user) {
        Map<String, Object> stats = readJsonMap(user.getChessOfflineStatsJson());
        int whiteWins = Math.max(0, toInt(stats.get("whiteWins"), 0));
        int blackWins = Math.max(0, toInt(stats.get("blackWins"), 0));
        int draws = Math.max(0, toInt(stats.get("draws"), 0));
        int totalGames = whiteWins + blackWins + draws;
        boolean hasProgress = totalGames > 0;
        String status = hasProgress
            ? totalGames + " van co vua offline da duoc luu trong ho so."
            : "Chua co du lieu. Danh Co vua offline hoac voi bot de cap nhat card nay.";
        return new PracticeStatCard(
            "chess-offline",
            "Chess offline / bot",
            "Offline Board",
            "bi-grid-3x3-gap-fill",
            status,
            "White / Black",
            whiteWins + "/" + blackWins,
            "Draws",
            String.valueOf(draws),
            "Total",
            String.valueOf(totalGames),
            "/chess/offline",
            hasProgress,
            hasProgress ? "Mo lai Chess" : "Bat dau Chess"
        );
    }

    private PracticeStatCard buildXiangqiOfflineCard(UserAccount user) {
        Map<String, Object> stats = readJsonMap(user.getXiangqiOfflineStatsJson());
        int redWins = Math.max(0, toInt(stats.get("redWins"), 0));
        int blackWins = Math.max(0, toInt(stats.get("blackWins"), 0));
        int draws = Math.max(0, toInt(stats.get("draws"), 0));
        int totalGames = redWins + blackWins + draws;
        boolean hasProgress = totalGames > 0;
        String status = hasProgress
            ? totalGames + " van co tuong offline da duoc dua vao profile."
            : "Chua co du lieu. Danh Co tuong offline hoac voi bot de bat dau.";
        return new PracticeStatCard(
            "xiangqi-offline",
            "Xiangqi offline / bot",
            "Offline Board",
            "bi-diagram-3-fill",
            status,
            "Red / Black",
            redWins + "/" + blackWins,
            "Draws",
            String.valueOf(draws),
            "Total",
            String.valueOf(totalGames),
            "/xiangqi/offline",
            hasProgress,
            hasProgress ? "Mo lai Xiangqi" : "Bat dau Xiangqi"
        );
    }

    private PracticeStatCard buildMinesweeperCard(UserAccount user) {
        Map<String, Object> stats = readJsonMap(user.getMinesweeperStatsJson());
        int totalGames = Math.max(0, toInt(stats.get("totalGames"), 0));
        int wins = Math.max(0, toInt(stats.get("wins"), 0));
        int losses = Math.max(0, toInt(stats.get("losses"), 0));
        Map<String, Object> bestTimesRaw = asStringObjectMap(stats.get("bestTimes"));
        int bestEasy = bestTimeValue(bestTimesRaw.get("preset:beginner"));
        int bestMedium = bestTimeValue(bestTimesRaw.get("preset:intermediate"));
        int bestHard = bestTimeValue(bestTimesRaw.get("preset:expert"));
        int fastest = java.util.stream.IntStream.of(bestEasy, bestMedium, bestHard)
            .filter(value -> value >= 0)
            .min()
            .orElse(-1);
        long trackedBoards = java.util.stream.Stream.of(bestEasy, bestMedium, bestHard)
            .filter(value -> value >= 0)
            .count();
        boolean hasProgress = totalGames > 0 || fastest >= 0;
        String status = hasProgress
            ? "Da luu " + wins + " tran thang va " + trackedBoards + " moc best time."
            : "Chua co du lieu. Mo Minesweeper va ket thuc mot van de hien thong ke.";
        return new PracticeStatCard(
            "minesweeper",
            "Minesweeper",
            "Offline Puzzle",
            "bi-grid-1x2-fill",
            status,
            "W / L",
            wins + "/" + losses,
            "Fastest",
            fastest >= 0 ? (fastest + "s") : "-",
            "Tracked PB",
            String.valueOf(trackedBoards),
            "/minesweeper",
            hasProgress,
            hasProgress ? "Mo lai Minesweeper" : "Bat dau Minesweeper"
        );
    }

    private PracticeStatCard buildQuizPracticeCard(UserAccount user) {
        Map<String, Object> stats = readJsonMap(user.getQuizPracticeStatsJson());
        int totalGames = Math.max(0, toInt(stats.get("totalGames"), 0));
        int wins = Math.max(0, toInt(stats.get("wins"), 0));
        int losses = Math.max(0, toInt(stats.get("losses"), 0));
        int draws = Math.max(0, toInt(stats.get("draws"), 0));
        int bestScore = Math.max(0, toInt(stats.get("bestScore"), 0));
        int perfectRounds = Math.max(0, toInt(stats.get("perfectRounds"), 0));
        boolean hasProgress = totalGames > 0 || bestScore > 0 || perfectRounds > 0;
        String status = hasProgress
            ? totalGames + " van local/bot da duoc luu vao account."
            : "Chua co du lieu. Choi Quiz local hoac dau bot de mo card nay.";
        return new PracticeStatCard(
            "quiz-practice",
            "Quiz local / bot",
            "Solo / Bot",
            "bi-patch-question-fill",
            status,
            "Best score",
            String.valueOf(bestScore),
            "W/L/D",
            wins + "/" + losses + "/" + draws,
            "Perfect runs",
            String.valueOf(perfectRounds),
            "/games/quiz/local",
            hasProgress,
            hasProgress ? "Choi them Quiz" : "Bat dau Quiz"
        );
    }

    private PracticeStatCard buildTypingPracticeCard(UserAccount user) {
        Map<String, Object> stats = readJsonMap(user.getTypingPracticeStatsJson());
        int totalGames = Math.max(0, toInt(stats.get("totalGames"), 0));
        int wins = Math.max(0, toInt(stats.get("wins"), 0));
        int losses = Math.max(0, toInt(stats.get("losses"), 0));
        int draws = Math.max(0, toInt(stats.get("draws"), 0));
        int bestWpm = Math.max(0, toInt(stats.get("bestWpm"), 0));
        double bestAccuracy = normalizePercentage(stats.get("bestAccuracy"));
        int completedQuotes = Math.max(0, toInt(stats.get("completedQuotes"), 0));
        boolean hasProgress = totalGames > 0 || bestWpm > 0 || completedQuotes > 0;
        String status = hasProgress
            ? totalGames + " van practice/bot da duoc dong bo vao ho so."
            : "Chua co du lieu. Chay Typing practice hoac race voi bot de bat dau.";
        return new PracticeStatCard(
            "typing-practice",
            "Typing practice / bot",
            "Practice Race",
            "bi-keyboard-fill",
            status,
            "Best WPM",
            String.valueOf(bestWpm),
            "Best accuracy",
            formatPercentage(bestAccuracy),
            "Completed",
            String.valueOf(completedQuotes),
            "/games/typing/practice",
            hasProgress,
            hasProgress ? "Chay them Typing" : "Bat dau Typing"
        );
    }

    private String toGameAchievementName(String gameName) {
        return "Winner - " + gameName;
    }

    private boolean isRepeatAchievement(String achievementName) {
        if (achievementName == null || achievementName.isBlank()) {
            return false;
        }
        return achievementName.startsWith("Ke co chap") || achievementName.startsWith("Khong the ngan can");
    }

    private boolean isGuestUserId(String userId) {
        return userId != null && userId.trim().toLowerCase().startsWith("guest-");
    }

    private List<FavoriteGameView> buildFavoriteGames(UserAccount user) {
        if (user == null) {
            return List.of();
        }
        Map<String, GameCatalogItem> catalog = gameCatalogService.findAll().stream()
            .collect(Collectors.toMap(
                item -> normalizeCode(item.code()),
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<FavoriteGameView> favorites = new ArrayList<>();
        for (String code : readStringList(user.getGamesBrowserFavoritesJson())) {
            String normalizedCode = normalizeCode(code);
            if (normalizedCode.isBlank()) {
                continue;
            }
            GameCatalogItem item = catalog.get(normalizedCode);
            favorites.add(item == null
                ? new FavoriteGameView(
                    normalizedCode,
                    normalizedCode.toUpperCase(),
                    "Game",
                    "bi-controller",
                    "Game duoc luu trong muc yeu thich cua nguoi choi.",
                    "/games/" + normalizedCode
                )
                : new FavoriteGameView(
                    item.code(),
                    item.displayName(),
                    item.shortLabel(),
                    item.iconClass(),
                    item.description(),
                    "/games/" + item.code()
                ));
        }
        return favorites;
    }

    private List<ActivityItem> buildRecentActivity(List<GameHistory> allGames, String userId) {
        if (allGames == null || allGames.isEmpty()) {
            return List.of();
        }
        return allGames.stream()
            .limit(6)
            .map(history -> toActivityItem(history, userId))
            .toList();
    }

    private ActivityItem toActivityItem(GameHistory history,
                                        String userId) {
        GameHistoryPresentationSupport.ViewMetadata metadata = gameHistoryPresentationSupport.describe(history);
        String normalizedCode = normalizeCode(metadata.gameCode());
        String displayName = metadata.gameName();
        String iconClass = metadata.gameIconClass();

        String resultLabel;
        String tone;
        if (history == null || history.getWinnerId() == null || history.getWinnerId().isBlank()) {
            resultLabel = "Hoa";
            tone = "draw";
        } else if (history.getWinnerId().equals(userId)) {
            resultLabel = "Thang";
            tone = "win";
        } else {
            resultLabel = "Thua";
            tone = "loss";
        }

        int totalMoves = history == null ? 0 : Math.max(0, history.getTotalMoves());
        String summary;
        if (totalMoves > 0) {
            summary = metadata.locationLabel() + " · " + metadata.matchCode() + " · " + totalMoves + " luot";
        } else {
            summary = metadata.locationLabel() + " · " + metadata.matchCode();
        }
        return new ActivityItem(
            normalizedCode.isBlank() ? "unknown" : normalizedCode,
            displayName,
            iconClass,
            resultLabel,
            tone,
            summary,
            metadata.matchCode(),
            metadata.locationLabel(),
            metadata.locationHref(),
            history == null ? null : history.getPlayedAt()
        );
    }

    private List<String> readStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(raw, STRING_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            return parsed.stream()
                .map(this::normalizeCode)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(raw, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> asStringObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
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
            try {
                parsed = Double.parseDouble(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return 0.0;
            }
        }
        double clamped = Math.max(0.0, Math.min(100.0, parsed));
        return Math.round(clamped * 10.0) / 10.0;
    }

    private int bestTimeValue(Object value) {
        int parsed = toInt(value, -1);
        return parsed >= 0 ? parsed : -1;
    }

    private String formatPercentage(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record FavoriteGameView(String code,
                                   String displayName,
                                   String shortLabel,
                                   String iconClass,
                                   String description,
                                   String href) {
    }

    public record ActivityItem(String gameCode,
                               String gameName,
                               String iconClass,
                               String resultLabel,
                               String resultTone,
                               String summary,
                               String matchCode,
                               String locationLabel,
                               String locationHref,
                               LocalDateTime playedAt) {
    }

    public record PracticeStatCard(String code,
                                   String title,
                                   String modeLabel,
                                   String iconClass,
                                   String status,
                                   String primaryLabel,
                                   String primaryValue,
                                   String secondaryLabel,
                                   String secondaryValue,
                                   String tertiaryLabel,
                                   String tertiaryValue,
                                   String href,
                                   boolean hasProgress,
                                   String ctaLabel) {
    }
}
