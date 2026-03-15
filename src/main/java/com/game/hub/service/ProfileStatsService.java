package com.game.hub.service;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProfileStatsService {
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

    public ProfileStatsService(GameHistoryRepository gameHistoryRepository,
                               UserAchievementRepository userAchievementRepository,
                               UserAccountRepository userAccountRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userAccountRepository = userAccountRepository;
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
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

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

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("totalGames", totalGames);
        result.put("winCount", winCount);
        result.put("winRate", winRate);
        result.put("rank", rank);
        result.put("danhHieu", danhHieu);
        result.put("winFastRanked", winFastRanked);
        result.put("bestStreak", maxStreak);
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
}
