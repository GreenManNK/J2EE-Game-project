package com.game.hub.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.games.cards.blackjack.logic.BlackjackRoom;
import com.game.hub.games.cards.blackjack.service.BlackjackService;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.games.typing.logic.TypingRoom;
import com.game.hub.games.typing.service.TypingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RawWebSocketOnlineIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TypingService typingService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private BlackjackService blackjackService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanRooms() {
        for (TypingRoom room : typingService.getAvailableRooms()) {
            typingService.removeRoom(room.getId());
        }
        for (QuizRoom room : quizService.getAvailableRooms()) {
            quizService.removeRoom(room.getRoomId());
        }
        for (BlackjackRoom room : blackjackService.getAvailableRooms()) {
            blackjackService.removeRoom(room.getId());
        }
    }

    @Test
    void typingEndpointShouldAcceptConnectionsAndBroadcastPlayingState() throws Exception {
        TestSocketListener firstListener = new TestSocketListener();
        TestSocketListener secondListener = new TestSocketListener();
        WebSocket firstSocket = openSocket("/game/typing", firstListener);
        WebSocket secondSocket = openSocket("/game/typing", secondListener);

        try {
            firstSocket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = firstListener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));
            assertFalse(roomId.isBlank());
            assertEquals("WAITING", String.valueOf(createdState.get("gameState")));
            assertEquals(1, ((Number) createdState.get("playerCount")).intValue());
            assertTrue(String.valueOf(createdState.get("yourId")).startsWith("guest-"));

            secondSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();

            Map<String, Object> firstCountdownState = awaitTypingState(firstListener, roomId, "COUNTDOWN", 2);
            Map<String, Object> secondCountdownState = awaitTypingState(secondListener, roomId, "COUNTDOWN", 2);

            assertEquals(roomId, firstCountdownState.get("id"));
            assertEquals(roomId, secondCountdownState.get("id"));

            TypingRoom room = typingService.getRoom(roomId);
            assertNotNull(room);
            forceTypingRoomIntoPlaying(room, firstSocket);

            Map<String, Object> firstPlayingState = awaitTypingState(firstListener, roomId, "PLAYING", 2);
            Map<String, Object> secondPlayingState = awaitTypingState(secondListener, roomId, "PLAYING", 2);

            assertEquals(roomId, firstPlayingState.get("id"));
            assertEquals("PLAYING", String.valueOf(firstPlayingState.get("gameState")));
            assertEquals(2, ((Number) firstPlayingState.get("playerCount")).intValue());

            assertEquals(roomId, secondPlayingState.get("id"));
            assertEquals("PLAYING", String.valueOf(secondPlayingState.get("gameState")));
            assertEquals(2, ((Number) secondPlayingState.get("playerCount")).intValue());
            assertTrue(String.valueOf(secondPlayingState.get("yourId")).startsWith("guest-"));
        } finally {
            closeQuietly(firstSocket);
            closeQuietly(secondSocket);
        }
    }

    @Test
    void typingEndpointShouldAllowAnotherPlayerToRejoinAfterDisconnect() throws Exception {
        TestSocketListener firstListener = new TestSocketListener();
        TestSocketListener secondListener = new TestSocketListener();
        TestSocketListener thirdListener = new TestSocketListener();
        WebSocket firstSocket = openSocket("/game/typing", firstListener);
        WebSocket secondSocket = openSocket("/game/typing", secondListener);
        WebSocket thirdSocket = openSocket("/game/typing", thirdListener);

        try {
            firstSocket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = firstListener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));

            secondSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();
            awaitTypingState(firstListener, roomId, "COUNTDOWN", 2);
            awaitTypingState(secondListener, roomId, "COUNTDOWN", 2);

            closeQuietly(secondSocket);
            Map<String, Object> waitingState = awaitTypingState(firstListener, roomId, "WAITING", 1);
            assertEquals(roomId, waitingState.get("id"));

            thirdSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();
            Map<String, Object> resumedCountdown = awaitTypingState(firstListener, roomId, "COUNTDOWN", 2);
            Map<String, Object> thirdCountdown = awaitTypingState(thirdListener, roomId, "COUNTDOWN", 2);

            assertEquals(roomId, resumedCountdown.get("id"));
            assertEquals(roomId, thirdCountdown.get("id"));

            TypingRoom room = typingService.getRoom(roomId);
            assertNotNull(room);
            forceTypingRoomIntoPlaying(room, firstSocket);

            Map<String, Object> resumedState = awaitTypingState(firstListener, roomId, "PLAYING", 2);
            Map<String, Object> thirdState = awaitTypingState(thirdListener, roomId, "PLAYING", 2);

            assertEquals(roomId, resumedState.get("id"));
            assertEquals(roomId, thirdState.get("id"));
            assertTrue(String.valueOf(thirdState.get("yourId")).startsWith("guest-"));
        } finally {
            closeQuietly(firstSocket);
            closeQuietly(thirdSocket);
        }
    }

    @Test
    void typingEndpointShouldBroadcastWinnerAfterProgressCompletesRace() throws Exception {
        TestSocketListener firstListener = new TestSocketListener();
        TestSocketListener secondListener = new TestSocketListener();
        WebSocket firstSocket = openSocket("/game/typing", firstListener);
        WebSocket secondSocket = openSocket("/game/typing", secondListener);

        try {
            firstSocket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = firstListener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));
            String playerId = String.valueOf(createdState.get("yourId"));
            String textToType = String.valueOf(createdState.get("textToType"));

            secondSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();
            awaitTypingState(firstListener, roomId, "COUNTDOWN", 2);
            awaitTypingState(secondListener, roomId, "COUNTDOWN", 2);

            TypingRoom room = typingService.getRoom(roomId);
            assertNotNull(room);
            forceTypingRoomIntoPlaying(room, firstSocket);
            awaitTypingState(firstListener, roomId, "PLAYING", 2);
            awaitTypingState(secondListener, roomId, "PLAYING", 2);

            firstSocket.sendText("{\"action\":\"progress\",\"roomId\":\"" + roomId + "\",\"typed\":" + objectMapper.writeValueAsString(textToType) + "}", true).join();

            Map<String, Object> finishedForFirst = firstListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && "FINISHED".equals(String.valueOf(json.get("gameState")))
                    && playerId.equals(String.valueOf(json.get("winner")))
            );
            Map<String, Object> finishedForSecond = secondListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && "FINISHED".equals(String.valueOf(json.get("gameState")))
                    && playerId.equals(String.valueOf(json.get("winner")))
            );

            assertEquals(playerId, finishedForFirst.get("winner"));
            assertEquals(playerId, finishedForSecond.get("winner"));
        } finally {
            closeQuietly(firstSocket);
            closeQuietly(secondSocket);
        }
    }

    @Test
    void quizEndpointShouldAcceptConnectionsAndBroadcastFirstQuestionAfterStart() throws Exception {
        TestSocketListener listener = new TestSocketListener();
        WebSocket socket = openSocket("/game/quiz", listener);

        try {
            socket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> roomState = listener.awaitJson();
            String roomId = String.valueOf(roomState.get("roomId"));
            assertFalse(roomId.isBlank());
            assertEquals(1, ((Number) roomState.get("players")).intValue());
            assertEquals(0, ((Number) roomState.get("spectators")).intValue());

            socket.sendText("{\"action\":\"start\"}", true).join();
            Map<String, Object> questionState = listener.awaitJsonMatching(json ->
                "What is the capital of France?".equals(json.get("question"))
                    && ((Number) json.get("questionNumber")).intValue() == 1
            );

            assertEquals("What is the capital of France?", questionState.get("question"));
            assertEquals(1, ((Number) questionState.get("questionNumber")).intValue());
            assertEquals(4, ((Number) questionState.get("totalQuestions")).intValue());
            assertEquals("singleCorrect", questionState.get("type"));
        } finally {
            closeQuietly(socket);
        }
    }

    @Test
    void quizEndpointShouldAdvanceToNextQuestionAfterAnswer() throws Exception {
        TestSocketListener listener = new TestSocketListener();
        WebSocket socket = openSocket("/game/quiz", listener);

        try {
            socket.sendText("{\"action\":\"create\"}", true).join();
            listener.awaitJson();

            socket.sendText("{\"action\":\"start\"}", true).join();
            Map<String, Object> firstQuestion = listener.awaitJsonMatching(json ->
                "What is the capital of France?".equals(json.get("question"))
                    && ((Number) json.get("questionNumber")).intValue() == 1
            );
            assertEquals("singleCorrect", firstQuestion.get("type"));

            socket.sendText("{\"action\":\"answer\",\"answer\":2}", true).join();
            Map<String, Object> secondQuestion = listener.awaitJsonMatching(json ->
                "What is 2 + 2?".equals(json.get("question"))
                    && ((Number) json.get("questionNumber")).intValue() == 2
            );

            assertEquals(4, ((Number) secondQuestion.get("totalQuestions")).intValue());
            assertEquals("singleCorrect", secondQuestion.get("type"));
        } finally {
            closeQuietly(socket);
        }
    }

    @Test
    void quizEndpointShouldKeepRoomAliveForSpectatorAndAllowPlayerRejoin() throws Exception {
        TestSocketListener playerListener = new TestSocketListener();
        TestSocketListener spectatorListener = new TestSocketListener();
        TestSocketListener rejoinListener = new TestSocketListener();
        WebSocket playerSocket = openSocket("/game/quiz", playerListener);
        WebSocket spectatorSocket = openSocket("/game/quiz", spectatorListener);
        WebSocket rejoinSocket = openSocket("/game/quiz", rejoinListener);

        try {
            playerSocket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = playerListener.awaitJson();
            String roomId = String.valueOf(createdState.get("roomId"));

            spectatorSocket.sendText("{\"action\":\"spectate\",\"roomId\":\"" + roomId + "\"}", true).join();
            playerListener.awaitJsonMatching(json ->
                roomId.equals(json.get("roomId"))
                    && ((Number) json.get("players")).intValue() == 1
                    && ((Number) json.get("spectators")).intValue() == 1
            );
            spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("roomId"))
                    && ((Number) json.get("players")).intValue() == 1
                    && ((Number) json.get("spectators")).intValue() == 1
            );

            closeQuietly(playerSocket);
            Map<String, Object> spectatorOnlyState = spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("roomId"))
                    && ((Number) json.get("players")).intValue() == 0
                    && ((Number) json.get("spectators")).intValue() == 1
            );
            assertEquals(roomId, spectatorOnlyState.get("roomId"));

            rejoinSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();
            Map<String, Object> rejoinStateForSpectator = spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("roomId"))
                    && ((Number) json.get("players")).intValue() == 1
                    && ((Number) json.get("spectators")).intValue() == 1
            );
            Map<String, Object> rejoinStateForPlayer = rejoinListener.awaitJsonMatching(json ->
                roomId.equals(json.get("roomId"))
                    && ((Number) json.get("players")).intValue() == 1
                    && ((Number) json.get("spectators")).intValue() == 1
            );

            assertEquals(roomId, rejoinStateForSpectator.get("roomId"));
            assertEquals(roomId, rejoinStateForPlayer.get("roomId"));
        } finally {
            closeQuietly(spectatorSocket);
            closeQuietly(rejoinSocket);
        }
    }

    @Test
    void blackjackEndpointShouldReturnToWaitingAfterPlayerStand() throws Exception {
        TestSocketListener listener = new TestSocketListener();
        WebSocket socket = openSocket("/game/blackjack", listener);

        try {
            socket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = listener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));

            socket.sendText("{\"action\":\"bet\",\"amount\":100}", true).join();
            Map<String, Object> roundState = listener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && "PLAYER_TURN".equals(String.valueOf(json.get("gameState")))
            );
            String playerId = String.valueOf(roundState.get("yourId"));

            socket.sendText("{\"action\":\"stand\",\"roomId\":\"" + roomId + "\"}", true).join();
            Map<String, Object> finishedRound = listener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && "WAITING".equals(String.valueOf(json.get("gameState")))
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> players = (Map<String, Object>) finishedRound.get("players");
            @SuppressWarnings("unchecked")
            Map<String, Object> playerState = (Map<String, Object>) players.get(playerId);

            assertEquals(0, ((Number) playerState.get("currentBet")).intValue());
            assertEquals("WAITING", String.valueOf(finishedRound.get("gameState")));
        } finally {
            closeQuietly(socket);
        }
    }

    @Test
    void blackjackEndpointShouldAcceptConnectionsAndBroadcastPlayerTurnAfterBet() throws Exception {
        TestSocketListener listener = new TestSocketListener();
        WebSocket socket = openSocket("/game/blackjack", listener);

        try {
            socket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = listener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));
            assertFalse(roomId.isBlank());
            assertEquals("WAITING", String.valueOf(createdState.get("gameState")));
            assertEquals(1, ((Number) createdState.get("playerCount")).intValue());

            socket.sendText("{\"action\":\"bet\",\"amount\":100}", true).join();
            Map<String, Object> roundState = listener.awaitJson();

            assertEquals(roomId, roundState.get("id"));
            assertEquals("PLAYER_TURN", String.valueOf(roundState.get("gameState")));
            assertEquals(1, ((Number) roundState.get("playerCount")).intValue());
            assertTrue(String.valueOf(roundState.get("yourId")).startsWith("guest-"));
            assertNotNull(roundState.get("dealer"));
        } finally {
            closeQuietly(socket);
        }
    }

    @Test
    void blackjackEndpointShouldKeepRoomAliveForSpectatorAndAllowPlayerRejoin() throws Exception {
        TestSocketListener playerListener = new TestSocketListener();
        TestSocketListener spectatorListener = new TestSocketListener();
        TestSocketListener rejoinListener = new TestSocketListener();
        WebSocket playerSocket = openSocket("/game/blackjack", playerListener);
        WebSocket spectatorSocket = openSocket("/game/blackjack", spectatorListener);
        WebSocket rejoinSocket = openSocket("/game/blackjack", rejoinListener);

        try {
            playerSocket.sendText("{\"action\":\"create\"}", true).join();
            Map<String, Object> createdState = playerListener.awaitJson();
            String roomId = String.valueOf(createdState.get("id"));

            spectatorSocket.sendText("{\"action\":\"spectate\",\"roomId\":\"" + roomId + "\"}", true).join();
            playerListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && ((Number) json.get("playerCount")).intValue() == 1
                    && ((java.util.List<?>) json.get("spectators")).size() == 1
            );
            spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && ((Number) json.get("playerCount")).intValue() == 1
                    && ((java.util.List<?>) json.get("spectators")).size() == 1
            );

            closeQuietly(playerSocket);
            Map<String, Object> spectatorOnlyState = spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && ((Number) json.get("playerCount")).intValue() == 0
                    && ((java.util.List<?>) json.get("spectators")).size() == 1
            );
            assertEquals(roomId, spectatorOnlyState.get("id"));

            rejoinSocket.sendText("{\"action\":\"join\",\"roomId\":\"" + roomId + "\"}", true).join();
            Map<String, Object> resumedForSpectator = spectatorListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && ((Number) json.get("playerCount")).intValue() == 1
                    && ((java.util.List<?>) json.get("spectators")).size() == 1
            );
            Map<String, Object> resumedForPlayer = rejoinListener.awaitJsonMatching(json ->
                roomId.equals(json.get("id"))
                    && ((Number) json.get("playerCount")).intValue() == 1
                    && ((java.util.List<?>) json.get("spectators")).size() == 1
            );

            assertEquals(roomId, resumedForSpectator.get("id"));
            assertEquals(roomId, resumedForPlayer.get("id"));
            assertTrue(String.valueOf(resumedForPlayer.get("yourId")).startsWith("guest-"));
        } finally {
            closeQuietly(spectatorSocket);
            closeQuietly(rejoinSocket);
        }
    }

    private WebSocket openSocket(String path, TestSocketListener listener) {
        return httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create("ws://127.0.0.1:" + port + "/Game" + path), listener)
            .join();
    }

    private void closeQuietly(WebSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "test").join();
        } catch (Exception ignored) {
            socket.abort();
        }
    }

    private Map<String, Object> awaitTypingState(TestSocketListener listener,
                                                 String roomId,
                                                 String gameState,
                                                 int playerCount) throws Exception {
        return listener.awaitJsonMatching(json ->
            roomId.equals(json.get("id"))
                && gameState.equals(String.valueOf(json.get("gameState")))
                && ((Number) json.get("playerCount")).intValue() == playerCount
        );
    }

    private void forceTypingRoomIntoPlaying(TypingRoom room, WebSocket triggerSocket) {
        ReflectionTestUtils.setField(room, "countdownEndsAtEpochMs", System.currentTimeMillis() - 1L);
        triggerSocket.sendText(
            "{\"action\":\"progress\",\"roomId\":\"" + room.getId() + "\",\"typed\":\"\"}",
            true
        ).join();
    }

    private final class TestSocketListener implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partialMessage = new StringBuilder();

        Map<String, Object> awaitJson() throws Exception {
            String payload = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(payload, "Timed out waiting for websocket payload");
            return objectMapper.readValue(payload, new TypeReference<>() {});
        }

        Map<String, Object> awaitJsonMatching(Predicate<Map<String, Object>> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                String payload = messages.poll(Math.max(1, remainingMs), TimeUnit.MILLISECONDS);
                if (payload == null) {
                    break;
                }
                Map<String, Object> json = objectMapper.readValue(payload, new TypeReference<>() {});
                if (predicate.test(json)) {
                    return json;
                }
            }
            throw new AssertionError("Timed out waiting for matching websocket payload");
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                messages.add(partialMessage.toString());
                partialMessage.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            messages.add("{\"error\":\"" + String.valueOf(error.getMessage()) + "\"}");
        }
    }
}
