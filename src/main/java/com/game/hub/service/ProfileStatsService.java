package com.game.hub.service;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProfileStatsService {
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

        Set<String> pointBased = Set.of("Bac", "Vang", "Kim cuong", "Thach dau");
        List<UserAchievement> userAchievements = userAchievementRepository.findByUserId(userId);

        List<String> diemAchievements = userAchievements.stream()
            .map(UserAchievement::getAchievementName)
            .filter(pointBased::contains)
            .distinct()
            .toList();

        Map<String, Long> repeatAchievements = new HashMap<>();
        for (UserAchievement a : userAchievements) {
            if (!pointBased.contains(a.getAchievementName())) {
                repeatAchievements.merge(a.getAchievementName(), 1L, Long::sum);
            }
        }

        List<String> freeAchievements = userAchievements.stream()
            .map(UserAchievement::getAchievementName)
            .filter(name -> name.startsWith("Ke co chap") || name.startsWith("Khong the ngan can"))
            .toList();

        List<String> allPossible = List.of(
            "Winner - Caro", "Winner - Chess", "Winner - Xiangqi", "Winner - Tien Len",
            "Winner - Typing", "Winner - Blackjack", "Winner - Quiz",
            "Bac", "Vang", "Kim cuong", "Thach dau",
            "Dau tri thang Phat", "Chien thang khong tuong", "Chuoi bat bai", "Ha guc vua",
            "Chuoi thua huyen thoai", "Choi vi dam me", "1 phut lo la",
            "Ke sat than", "That bai xung dang", "Chien thang kho khan",
            "Ke co chap", "Khong the ngan can"
        );

        Set<String> achieved = new HashSet<>();
        achieved.addAll(diemAchievements);
        achieved.addAll(repeatAchievements.keySet());

        List<String> locked = allPossible.stream().filter(a -> !achieved.contains(a)).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("totalGames", totalGames);
        result.put("winCount", winCount);
        result.put("winRate", winRate);
        result.put("rank", rank);
        result.put("danhHieu", danhHieu);
        result.put("winFastRanked", winFastRanked);
        result.put("bestStreak", maxStreak);
        result.put("diemAchievements", diemAchievements);
        result.put("repeatAchievements", repeatAchievements);
        result.put("achievements", freeAchievements);
        result.put("lockedAchievements", locked);
        result.put("isOwner", userId.equals(currentUserId));
        return result;
    }
}
