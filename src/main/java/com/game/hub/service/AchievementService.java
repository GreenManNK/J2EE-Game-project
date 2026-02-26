package com.game.hub.service;

import com.game.hub.entity.AchievementNotification;
import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAchievement;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.AchievementNotificationRepository;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAchievementRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AchievementService {
    private static final String BAC = "Bac";
    private static final String VANG = "Vang";
    private static final String KIM_CUONG = "Kim cuong";
    private static final String THACH_DAU = "Thach dau";
    private static final String DAU_TRI_THANG_PHAT = "Dau tri thang Phat";
    private static final String CHIEN_THANG_KHONG_TUONG = "Chien thang khong tuong";
    private static final String CHUOI_BAT_BAI = "Chuoi bat bai";
    private static final String HA_GUC_VUA = "Ha guc vua";
    private static final String CHUOI_THUA_HUYEN_THOAI = "Chuoi thua huyen thoai";
    private static final String CHOI_VI_DAM_ME = "Choi vi dam me";
    private static final String MOT_PHUT_LO_LA = "1 phut lo la";
    private static final String KE_SAT_THAN = "Ke sat than";
    private static final String THAT_BAI_XUNG_DANG = "That bai xung dang";
    private static final String CHIEN_THANG_KHO_KHAN = "Chien thang kho khan";
    private static final String KE_CO_CHAP = "Ke co chap";
    private static final String KHONG_THE_NGAN_CAN = "Khong the ngan can";

    private final UserAchievementRepository userAchievementRepository;
    private final AchievementNotificationRepository achievementNotificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final GameHistoryRepository gameHistoryRepository;

    public AchievementService(UserAchievementRepository userAchievementRepository,
                              AchievementNotificationRepository achievementNotificationRepository,
                              UserAccountRepository userAccountRepository,
                              GameHistoryRepository gameHistoryRepository) {
        this.userAchievementRepository = userAchievementRepository;
        this.achievementNotificationRepository = achievementNotificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.gameHistoryRepository = gameHistoryRepository;
    }

    @Transactional
    public void evaluateAfterMatch(String roomId,
                                   String winnerId,
                                   String loserId,
                                   int totalMoves,
                                   Integer winnerScoreBefore,
                                   Integer loserScoreBefore,
                                   String topPlayerIdBeforeMatch) {
        if (winnerId == null || winnerId.isBlank() || loserId == null || loserId.isBlank()) {
            return;
        }

        UserAccount winner = userAccountRepository.findById(winnerId).orElse(null);
        UserAccount loser = userAccountRepository.findById(loserId).orElse(null);
        if (winner == null || loser == null) {
            return;
        }

        PlayerStats winnerStats = buildStats(winnerId);
        PlayerStats loserStats = buildStats(loserId);

        evaluateScoreMilestones(winner);
        evaluateScoreMilestones(loser);

        if (winnerStats.totalGames() >= 30) {
            grantOnce(winnerId, CHOI_VI_DAM_ME);
        }

        if (winnerStats.currentWinStreak() >= 5) {
            grantOnce(winnerId, CHUOI_BAT_BAI);
        }

        if (loserStats.currentLoseStreak() >= 5) {
            grantOnce(loserId, CHUOI_THUA_HUYEN_THOAI);
        }

        if (winnerStats.totalGames() >= 10 && winnerStats.winRate() >= 80.0) {
            grantOnce(winnerId, CHIEN_THANG_KHONG_TUONG);
        }

        if (totalMoves <= 8) {
            grantOnce(loserId, MOT_PHUT_LO_LA);
        }

        if (topPlayerIdBeforeMatch != null && topPlayerIdBeforeMatch.equals(loserId)) {
            grantOnce(winnerId, HA_GUC_VUA);
        }

        if (winnerScoreBefore != null && loserScoreBefore != null) {
            int scoreGap = winnerScoreBefore - loserScoreBefore;

            if (scoreGap <= -150) {
                grantOnce(winnerId, CHIEN_THANG_KHO_KHAN);
            }

            if (scoreGap >= 150) {
                grantOnce(loserId, THAT_BAI_XUNG_DANG);
            }

            if (scoreGap <= -250) {
                grantOnce(winnerId, KE_SAT_THAN);
            }

            if (roomId != null && roomId.startsWith("Ranked_")
                && totalMoves >= 25 && scoreGap <= -100) {
                grantOnce(winnerId, DAU_TRI_THANG_PHAT);
            }
        }

        if (winnerStats.totalWins() > 0 && winnerStats.totalWins() % 10 == 0) {
            grantRepeat(winnerId, KE_CO_CHAP, winnerStats.totalWins());
        }

        if (winnerStats.currentWinStreak() >= 7) {
            int milestone = (winnerStats.currentWinStreak() / 7) * 7;
            grantRepeat(winnerId, KHONG_THE_NGAN_CAN, milestone);
        }
    }

    private void evaluateScoreMilestones(UserAccount user) {
        int score = user.getScore();
        String userId = user.getId();
        if (score >= 100) {
            grantOnce(userId, BAC);
        }
        if (score >= 300) {
            grantOnce(userId, VANG);
        }
        if (score >= 600) {
            grantOnce(userId, KIM_CUONG);
        }
        if (score >= 1000) {
            grantOnce(userId, THACH_DAU);
        }
    }

    private PlayerStats buildStats(String userId) {
        List<GameHistory> games = gameHistoryRepository.findByPlayer1IdOrPlayer2IdOrderByPlayedAtDesc(userId, userId);
        int totalGames = games.size();
        int totalWins = (int) games.stream().filter(g -> userId.equals(g.getWinnerId())).count();
        double winRate = totalGames == 0 ? 0.0 : (totalWins * 100.0) / totalGames;

        int currentWinStreak = 0;
        int currentLoseStreak = 0;
        for (GameHistory game : games) {
            boolean win = userId.equals(game.getWinnerId());
            if (win && currentLoseStreak == 0) {
                currentWinStreak++;
            } else if (!win && currentWinStreak == 0) {
                currentLoseStreak++;
            } else {
                break;
            }
        }

        return new PlayerStats(totalGames, totalWins, winRate, currentWinStreak, currentLoseStreak);
    }

    private void grantRepeat(String userId, String baseName, int milestone) {
        grantOnce(userId, baseName);
        grantOnce(userId, baseName + " " + milestone);
    }

    private void grantOnce(String userId, String achievementName) {
        if (userAchievementRepository.existsByUserIdAndAchievementName(userId, achievementName)) {
            return;
        }

        UserAchievement achievement = new UserAchievement();
        achievement.setUserId(userId);
        achievement.setAchievementName(achievementName);
        achievement.setAchievedAt(LocalDateTime.now());
        userAchievementRepository.save(achievement);

        AchievementNotification notification = new AchievementNotification();
        notification.setUserId(userId);
        notification.setAchievementName(achievementName);
        achievementNotificationRepository.save(notification);
    }

    private record PlayerStats(int totalGames, int totalWins, double winRate, int currentWinStreak, int currentLoseStreak) {
    }
}
