package com.game.hub.games.caro.service;

import com.game.hub.entity.GameHistory;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.GameHistoryRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.AchievementService;
import com.game.hub.service.WinningStreakService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameRoomServiceTest {

    @Test
    void shouldRejectMoveWhenRoomHasOnlyOnePlayer() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class),
            mock(WinningStreakService.class)
        );

        GameRoomService.JoinResult join = service.joinRoom("room-a", "u1");
        assertTrue(join.ok());
        assertEquals("u1", join.currentTurnUserId());
        assertEquals(1, join.playerCount());

        GameRoomService.MoveResult move = service.makeMove("room-a", "u1", 0, 0);
        assertFalse(move.ok());
        assertEquals("Waiting for opponent", move.error());
    }

    @Test
    void leaveRoomShouldResetBoardAndAllowNewPlayerToGetMissingSymbol() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class),
            mock(WinningStreakService.class)
        );

        GameRoomService.JoinResult first = service.joinRoom("room-b", "u1");
        GameRoomService.JoinResult second = service.joinRoom("room-b", "u2");
        assertTrue(first.ok());
        assertTrue(second.ok());
        assertEquals("X", first.symbol());
        assertEquals("O", second.symbol());

        GameRoomService.MoveResult move = service.makeMove("room-b", "u1", 0, 0);
        assertTrue(move.ok());
        assertEquals("X", service.getBoardSnapshot("room-b")[0][0]);

        service.leaveRoom("room-b", "u1");

        assertNull(service.getCurrentTurnUserId("room-b"));
        assertNull(service.getBoardSnapshot("room-b")[0][0]);

        GameRoomService.JoinResult third = service.joinRoom("room-b", "u3");
        assertTrue(third.ok());
        assertEquals("X", third.symbol());
        assertEquals("u3", third.currentTurnUserId());
    }

    @Test
    void spectatorShouldBeAbleToWatchWithoutBeingAllowedToMove() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class),
            mock(WinningStreakService.class)
        );

        service.joinRoom("room-spec", "u1");
        service.joinRoom("room-spec", "u2");

        GameRoomService.JoinResult spectator = service.joinAsSpectator("room-spec", "viewer-1");
        GameRoomService.MoveResult move = service.makeMove("room-spec", "viewer-1", 0, 0);

        assertTrue(spectator.ok());
        assertEquals("spectator", spectator.symbol());
        assertEquals(2, spectator.playerCount());
        assertEquals(1, spectator.spectatorCount());
        assertFalse(move.ok());
        assertEquals("Player does not belong to room", move.error());
    }

    @Test
    void spectatorShouldBeAbleToJoinRoomBeforePlayersWithoutThrowing() {
        GameRoomService service = new GameRoomService(
            mock(UserAccountRepository.class),
            mock(GameHistoryRepository.class),
            mock(AchievementService.class),
            mock(WinningStreakService.class)
        );

        GameRoomService.JoinResult spectator = service.joinAsSpectator("room-empty", "viewer-1");

        assertTrue(spectator.ok());
        assertEquals(0, spectator.playerCount());
        assertEquals(1, spectator.spectatorCount());
        assertNull(service.getCurrentTurnUserId("room-empty"));
    }

    @Test
    void winningMatchShouldPersistStructuredHistoryMetadata() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        WinningStreakService winningStreakService = mock(WinningStreakService.class);
        when(userAccountRepository.findById("u1")).thenReturn(Optional.empty());
        when(userAccountRepository.findById("u2")).thenReturn(Optional.empty());
        when(userAccountRepository.findTopByOrderByScoreDesc()).thenReturn(Optional.empty());
        doNothing().when(achievementService).evaluateAfterMatch(any(), any(), any(), anyInt(), any(), any(), any());

        GameRoomService service = new GameRoomService(
            userAccountRepository,
            gameHistoryRepository,
            achievementService,
            winningStreakService
        );

        service.joinRoom("Normal_ABC123", "u1");
        service.joinRoom("Normal_ABC123", "u2");
        service.makeMove("Normal_ABC123", "u1", 0, 0);
        service.makeMove("Normal_ABC123", "u2", 1, 0);
        service.makeMove("Normal_ABC123", "u1", 0, 1);
        service.makeMove("Normal_ABC123", "u2", 1, 1);
        service.makeMove("Normal_ABC123", "u1", 0, 2);
        service.makeMove("Normal_ABC123", "u2", 1, 2);
        service.makeMove("Normal_ABC123", "u1", 0, 3);
        service.makeMove("Normal_ABC123", "u2", 1, 3);

        GameRoomService.MoveResult win = service.makeMove("Normal_ABC123", "u1", 0, 4);

        assertTrue(win.ok());
        assertTrue(win.win());

        ArgumentCaptor<GameHistory> captor = ArgumentCaptor.forClass(GameHistory.class);
        verify(gameHistoryRepository).save(captor.capture());
        GameHistory saved = captor.getValue();
        assertEquals("caro", saved.getGameCode());
        assertEquals("Normal_ABC123", saved.getRoomId());
        assertEquals("Phong thuong Caro", saved.getLocationLabel());
        assertEquals("/game/room/Normal_ABC123", saved.getLocationPath());
        assertTrue(saved.getMatchCode().startsWith("Normal_ABC123-"));
    }

    @Test
    void normalRoomShouldNotGrantSkillOrAllowSkillUse() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        WinningStreakService winningStreakService = mock(WinningStreakService.class);

        UserAccount winner = new UserAccount();
        winner.setId("u1");
        winner.setScore(100);
        UserAccount loser = new UserAccount();
        loser.setId("u2");
        loser.setScore(100);

        when(userAccountRepository.findById("u1")).thenReturn(Optional.of(winner));
        when(userAccountRepository.findById("u2")).thenReturn(Optional.of(loser));
        when(userAccountRepository.findTopByOrderByScoreDesc()).thenReturn(Optional.of(winner));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(achievementService).evaluateAfterMatch(any(), any(), any(), anyInt(), any(), any(), any());

        GameRoomService service = new GameRoomService(
            userAccountRepository,
            gameHistoryRepository,
            achievementService,
            winningStreakService
        );

        service.joinRoom("Normal_SKILL", "u1");
        service.joinRoom("Normal_SKILL", "u2");
        service.makeMove("Normal_SKILL", "u1", 0, 0);
        service.makeMove("Normal_SKILL", "u2", 1, 0);
        service.makeMove("Normal_SKILL", "u1", 0, 1);
        service.makeMove("Normal_SKILL", "u2", 1, 1);
        service.makeMove("Normal_SKILL", "u1", 0, 2);
        service.makeMove("Normal_SKILL", "u2", 1, 2);
        service.makeMove("Normal_SKILL", "u1", 0, 3);
        service.makeMove("Normal_SKILL", "u2", 1, 3);

        GameRoomService.MoveResult win = service.makeMove("Normal_SKILL", "u1", 0, 4);

        assertTrue(win.ok());
        assertTrue(win.win());
        assertTrue(service.getSkillChargesSnapshot("Normal_SKILL").isEmpty());
        assertTrue(service.getSkillTypesSnapshot("Normal_SKILL").isEmpty());

        GameRoomService.SkillResult skill = service.useSkill("Normal_SKILL", "u1");

        assertFalse(skill.ok());
        assertEquals("Skill chi duoc dung trong che do nang cao", skill.error());
    }

    @Test
    void advancedRoomShouldGrantSkillAndAllowRemovingRandomOpponentPiece() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        WinningStreakService winningStreakService = mock(WinningStreakService.class);

        GameRoomService service = new GameRoomService(
            userAccountRepository,
            gameHistoryRepository,
            achievementService,
            winningStreakService
        );

        service.joinRoom("Advanced_SKILL", "u1");
        service.joinRoom("Advanced_SKILL", "u2");
        service.makeMove("Advanced_SKILL", "u1", 0, 0);
        service.makeMove("Advanced_SKILL", "u2", 1, 0);
        service.makeMove("Advanced_SKILL", "u1", 0, 1);
        service.makeMove("Advanced_SKILL", "u2", 1, 1);
        service.makeMove("Advanced_SKILL", "u1", 0, 2);
        service.makeMove("Advanced_SKILL", "u2", 1, 2);
        service.makeMove("Advanced_SKILL", "u1", 0, 3);
        service.makeMove("Advanced_SKILL", "u2", 1, 3);

        GameRoomService.MoveResult awarded = service.makeMove("Advanced_SKILL", "u1", 0, 4);

        assertTrue(awarded.ok());
        assertTrue(awarded.skillAwarded());
        assertEquals(1, service.getSkillChargesSnapshot("Advanced_SKILL").get("u1"));

        service.resetRoom("Advanced_SKILL");
        service.makeMove("Advanced_SKILL", "u1", 2, 2);
        service.makeMove("Advanced_SKILL", "u2", 5, 5);

        GameRoomService.SkillResult skill = service.useSkill("Advanced_SKILL", "u1");

        assertTrue(skill.ok());
        assertEquals("u2", skill.affectedUserId());
        assertEquals("u2", skill.nextTurnUserId());
        assertEquals(0, skill.remainingCharges());
        assertNull(service.getBoardSnapshot("Advanced_SKILL")[5][5]);
        assertEquals(Map.of("u1", 0, "u2", 0), skill.skillCharges());
    }

    @Test
    void advancedRoomShouldAwardRandomSkillInsteadOfEndingRoundAtFiveInRow() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        WinningStreakService winningStreakService = mock(WinningStreakService.class);

        GameRoomService service = new GameRoomService(
            userAccountRepository,
            gameHistoryRepository,
            achievementService,
            winningStreakService
        );

        service.joinRoom("Advanced_ROOM01", "u1");
        service.joinRoom("Advanced_ROOM01", "u2");
        service.makeMove("Advanced_ROOM01", "u1", 0, 0);
        service.makeMove("Advanced_ROOM01", "u2", 1, 0);
        service.makeMove("Advanced_ROOM01", "u1", 0, 1);
        service.makeMove("Advanced_ROOM01", "u2", 1, 1);
        service.makeMove("Advanced_ROOM01", "u1", 0, 2);
        service.makeMove("Advanced_ROOM01", "u2", 1, 2);
        service.makeMove("Advanced_ROOM01", "u1", 0, 3);
        service.makeMove("Advanced_ROOM01", "u2", 1, 3);

        GameRoomService.MoveResult result = service.makeMove("Advanced_ROOM01", "u1", 0, 4);

        assertTrue(result.ok());
        assertFalse(result.win());
        assertFalse(result.draw());
        assertTrue(result.skillAwarded());
        assertEquals("u1", result.awardedUserId());
        assertEquals("u2", result.nextTurnUserId());
        assertNotNull(result.awardedSkillType());
        assertTrue(List.of("REMOVE_RANDOM_PIECE", "REMOVE_DOUBLE_RANDOM_PIECE", "EXTRA_TURN").contains(result.awardedSkillType()));
        assertEquals(1, service.getSkillChargesSnapshot("Advanced_ROOM01").get("u1"));
        assertEquals(0, service.getSkillChargesSnapshot("Advanced_ROOM01").get("u2"));
        verify(gameHistoryRepository, never()).save(any(GameHistory.class));
        verify(winningStreakService, never()).recordMatchResult(any(), any());
    }
}
