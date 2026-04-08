package com.game.hub.games.cards.tienlen.websocket;

import com.game.hub.games.cards.tienlen.service.TienLenRoomService;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.AchievementService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TienLenWebSocketControllerTest {

    @Test
    void playShouldAwardTienLenAchievementToHumanWinner() {
        TienLenRoomService roomService = mock(TienLenRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(roomService.availableRooms()).thenReturn(List.of());

        TienLenRoomService.RoomSnapshot gameOverRoom = new TienLenRoomService.RoomSnapshot(
            "TL-WIN",
            true,
            true,
            "u1",
            null,
            null,
            8,
            4,
            4,
            false,
            "u1 het bai",
            List.of(
                player("u1", 0, false),
                player("u2", 1, false),
                player("u3", 2, false),
                player("u4", 3, false)
            ),
            List.of(),
            null,
            "u1 thang",
            1
        );
        when(roomService.playCards("TL-WIN", "u1", List.of("3S")))
            .thenReturn(TienLenRoomService.ActionResult.ok(gameOverRoom, "GAME_OVER"));
        when(roomService.resetToWaitingAfterGame("TL-WIN")).thenReturn(null);

        TienLenWebSocketController controller = new TienLenWebSocketController(
            roomService,
            messagingTemplate,
            userAccountRepository,
            achievementService
        );
        try {
            TienLenPlayMessage message = new TienLenPlayMessage();
            message.setRoomId("TL-WIN");
            message.setUserId("u1");
            message.setCardCodes(List.of("3S"));

            controller.play(message, headers);

            verify(achievementService).recordRewardedWin("u1", "Tien Len");
        } finally {
            controller.shutdownAutomation();
        }
    }

    @Test
    void playShouldSkipTienLenAchievementWhenBotWins() {
        TienLenRoomService roomService = mock(TienLenRoomService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        AchievementService achievementService = mock(AchievementService.class);
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        when(headers.getSessionAttributes()).thenReturn(Map.of("AUTH_USER_ID", "u1"));
        when(roomService.availableRooms()).thenReturn(List.of());

        TienLenRoomService.RoomSnapshot gameOverRoom = new TienLenRoomService.RoomSnapshot(
            "TL-BOT",
            true,
            true,
            "bot-tienlen-1",
            null,
            null,
            10,
            4,
            4,
            false,
            "Bot 1 het bai",
            List.of(
                player("u1", 0, false),
                player("u2", 1, false),
                player("bot-tienlen-1", 2, true),
                player("bot-tienlen-2", 3, true)
            ),
            List.of(),
            null,
            "Bot 1 thang",
            1
        );
        when(roomService.playCards("TL-BOT", "u1", List.of("3S")))
            .thenReturn(TienLenRoomService.ActionResult.ok(gameOverRoom, "GAME_OVER"));
        when(roomService.resetToWaitingAfterGame("TL-BOT")).thenReturn(null);

        TienLenWebSocketController controller = new TienLenWebSocketController(
            roomService,
            messagingTemplate,
            userAccountRepository,
            achievementService
        );
        try {
            TienLenPlayMessage message = new TienLenPlayMessage();
            message.setRoomId("TL-BOT");
            message.setUserId("u1");
            message.setCardCodes(List.of("3S"));

            controller.play(message, headers);

            verify(achievementService, never()).checkAndAward("bot-tienlen-1", "Tien Len", true);
        } finally {
            controller.shutdownAutomation();
        }
    }

    private static TienLenRoomService.PlayerSnapshot player(String userId, int seatIndex, boolean bot) {
        return new TienLenRoomService.PlayerSnapshot(
            userId,
            userId,
            "",
            seatIndex,
            bot,
            0,
            0,
            0,
            0,
            0,
            false,
            0,
            0
        );
    }
}
