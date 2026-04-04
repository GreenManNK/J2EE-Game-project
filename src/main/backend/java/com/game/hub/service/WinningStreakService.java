package com.game.hub.service;

import com.game.hub.entity.UserAccount;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WinningStreakService {
    private static final int RED_CHESS_PIECE_THRESHOLD = 10;

    private final UserAccountRepository userAccountRepository;

    public WinningStreakService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public MatchResult recordMatchResult(String winnerId, String loserId) {
        PlayerState winner = incrementWinner(winnerId);
        PlayerState loser = resetLoser(loserId);
        return new MatchResult(winner, loser);
    }

    @Transactional(readOnly = true)
    public PlayerState currentState(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return null;
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return new PlayerState(normalizedUserId, 0, false);
        }
        return new PlayerState(user.getId(), user.getWinningStreak(), hasRedChessPieces(user.getWinningStreak()));
    }

    private PlayerState incrementWinner(String winnerId) {
        String normalizedWinnerId = trimToNull(winnerId);
        if (normalizedWinnerId == null) {
            return null;
        }
        UserAccount user = userAccountRepository.findById(normalizedWinnerId).orElse(null);
        if (user == null) {
            return new PlayerState(normalizedWinnerId, 0, false);
        }
        user.setWinningStreak(user.getWinningStreak() + 1);
        userAccountRepository.save(user);
        return new PlayerState(user.getId(), user.getWinningStreak(), hasRedChessPieces(user.getWinningStreak()));
    }

    private PlayerState resetLoser(String loserId) {
        String normalizedLoserId = trimToNull(loserId);
        if (normalizedLoserId == null) {
            return null;
        }
        UserAccount user = userAccountRepository.findById(normalizedLoserId).orElse(null);
        if (user == null) {
            return new PlayerState(normalizedLoserId, 0, false);
        }
        user.setWinningStreak(0);
        userAccountRepository.save(user);
        return new PlayerState(user.getId(), user.getWinningStreak(), false);
    }

    private boolean hasRedChessPieces(int winningStreak) {
        return winningStreak > RED_CHESS_PIECE_THRESHOLD;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record MatchResult(PlayerState winner, PlayerState loser) {
    }

    public record PlayerState(String userId, int winningStreak, boolean redChessPieces) {
    }
}
